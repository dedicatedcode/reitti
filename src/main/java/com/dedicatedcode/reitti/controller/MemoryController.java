package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.controller.error.PageNotFoundException;
import com.dedicatedcode.reitti.model.integration.ImmichIntegration;
import com.dedicatedcode.reitti.model.memory.HeaderType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.memory.MemoryBlockPart;
import com.dedicatedcode.reitti.model.memory.MemoryOverviewDTO;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.service.MemoryService;
import com.dedicatedcode.reitti.service.MagicLinkTokenService;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Controller
@RequestMapping("/memories")
public class MemoryController {

    private final MemoryService memoryService;
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final ImmichIntegrationService immichIntegrationService;
    private final MagicLinkTokenService magicLinkTokenService;

    public MemoryController(MemoryService memoryService,
                            TripJdbcService tripJdbcService,
                            ProcessedVisitJdbcService processedVisitJdbcService,
                            ImmichIntegrationService immichIntegrationService,
                            MagicLinkTokenService magicLinkTokenService) {
        this.memoryService = memoryService;
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.immichIntegrationService = immichIntegrationService;
        this.magicLinkTokenService = magicLinkTokenService;
    }

    @GetMapping
    public String get() {
        return "memories/list";
    }

    @GetMapping("/years-navigation")
    public String listMemories(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("years", memoryService.getAvailableYears(user));
        return "memories/fragments :: years-navigation";
    }

    @GetMapping("/all")
    public String getAll(@AuthenticationPrincipal User user, @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone, Model model) {
        model.addAttribute("memories", this.memoryService.getMemoriesForUser(user).stream().map(m -> {
            String startDateLocal = m.getStartDate().atZone(timezone).toLocalDate().toString();
            String endDateLocal = m.getEndDate().atZone(timezone).toLocalDate().toString();

            String rawLocationUrl = "/api/v1/raw-location-points?startDate=" + startDateLocal + "&endDate=" + endDateLocal;
            return new MemoryOverviewDTO(m, rawLocationUrl);
        }).toList());
        model.addAttribute("year", "all");
        return "memories/fragments :: memories-list";
    }

    @GetMapping("/year/{year}")
    public String getYear(@AuthenticationPrincipal User user, @PathVariable int year, @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone, Model model) {
        model.addAttribute("memories", this.memoryService.getMemoriesForUserAndYear(user, year)
                .stream().map(m -> {
                    String startDateLocal = m.getStartDate().atZone(timezone).toLocalDate().toString();
                    String endDateLocal = m.getEndDate().atZone(timezone).toLocalDate().toString();

                    String rawLocationUrl = "/api/v1/raw-location-points?startDate=" + startDateLocal + "&endDate=" + endDateLocal;
                    return new MemoryOverviewDTO(m, rawLocationUrl);
                }).toList());
        model.addAttribute("year", year);
        return "memories/fragments :: memories-list";
    }

    @GetMapping("/{id}")
    public String viewMemory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
            Model model) {
        Memory memory = memoryService.getMemoryById(user, id)
                .orElseThrow(() -> new PageNotFoundException("Memory not found"));
        
        model.addAttribute("memory", memory);

        List<MemoryBlockPart> blocks = memoryService.getBlockPartsForMemory(user, id, timezone);
        model.addAttribute("blocks", blocks);
        
        String startDateLocal = memory.getStartDate().atZone(timezone).toLocalDate().toString();
        String endDateLocal = memory.getEndDate().atZone(timezone).toLocalDate().toString();
        
        String rawLocationUrl = "/api/v1/raw-location-points?startDate=" + startDateLocal + "&endDate=" + endDateLocal;
        model.addAttribute("rawLocationUrl", rawLocationUrl);
        model.addAttribute("canEdit", true);
        model.addAttribute("isOwner", true);
        return "memories/view";
    }

    @GetMapping("/new")
    public String newMemoryForm(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String year,
            Model model) {
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("year", year);
        return "memories/new :: new-memory";
    }

    @PostMapping
    public String createMemory(
            @AuthenticationPrincipal User user,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam(required = false) String headerImageUrl,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
            Model model) {

        if (title == null || title.trim().isEmpty()) {
            model.addAttribute("error", "memory.validation.title.required");
            model.addAttribute("title", title);
            model.addAttribute("description", description);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
            model.addAttribute("headerImageUrl", headerImageUrl);
            return "memories/new :: new-memory";
        }
        
        try {
            Instant start = ZonedDateTime.of(startDate.atStartOfDay(), timezone).toInstant();
            Instant end = ZonedDateTime.of(endDate.plusDays(1).atStartOfDay().minusNanos(1), timezone).toInstant();
            Instant today = Instant.now();
            
            // Validate dates are not in the future
            if (start.isAfter(today) || end.isAfter(today)) {
                model.addAttribute("error", "memory.validation.date.future");
                model.addAttribute("title", title);
                model.addAttribute("description", description);
                model.addAttribute("startDate", startDate);
                model.addAttribute("endDate", endDate);
                model.addAttribute("headerImageUrl", headerImageUrl);
                return "memories/new :: new-memory";
            }
            
            // Validate end date is not before start date
            if (end.isBefore(start)) {
                model.addAttribute("error", "memory.validation.end.date.before.start");
                model.addAttribute("title", title);
                model.addAttribute("description", description);
                model.addAttribute("startDate", startDate);
                model.addAttribute("endDate", endDate);
                model.addAttribute("headerImageUrl", headerImageUrl);
                return "memories/new :: new-memory";
            }
            
            Memory memory = new Memory(
                    title.trim(),
                    description != null ? description.trim() : null,
                    start,
                    end,
                    HeaderType.MAP,
                    headerImageUrl
            );
            
            Memory created = memoryService.createMemory(user, memory);
            this.memoryService.recalculateMemory(user, created.getId(), timezone);
            
            return "redirect:/memories/" + created.getId();
            
        } catch (Exception e) {
            model.addAttribute("error", "memory.validation.start.date.required");
            model.addAttribute("title", title);
            model.addAttribute("description", description);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
            model.addAttribute("headerImageUrl", headerImageUrl);
            return "memories/new :: new-memory";
        }
    }

    @GetMapping("/{id}/edit")
    public String editMemoryForm(@AuthenticationPrincipal User user, @PathVariable Long id, @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
            Model model) {
        Memory memory = memoryService.getMemoryById(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        model.addAttribute("memory", memory);
        model.addAttribute("startDate", memory.getStartDate().atZone(timezone).toLocalDate());
        model.addAttribute("endDate", memory.getEndDate().atZone(timezone).toLocalDate());
        return "memories/edit :: edit-memory";
    }

    @PostMapping("/{id}")
    public String updateMemory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam HeaderType headerType,
            @RequestParam(required = false) String headerImageUrl,
            @RequestParam Long version,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
            Model model) {
        
        Memory memory = memoryService.getMemoryById(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        // Add validation similar to create method
        if (title == null || title.trim().isEmpty()) {
            model.addAttribute("error", "memory.validation.title.required");
            model.addAttribute("memory", memory);
            
            model.addAttribute("cancelEndpoint", "/memories/" + id);
            model.addAttribute("cancelTarget", ".memory-header");
            model.addAttribute("formTarget", ".memory-header");
            return "memories/edit :: edit-memory";
        }
        
        try {
            Instant start = ZonedDateTime.of(startDate.atStartOfDay(), timezone).toInstant();
            Instant end = ZonedDateTime.of(endDate.plusDays(1).atStartOfDay().minusSeconds(1), timezone).toInstant();
            Instant today = Instant.now();
            
            if (start.isAfter(today) || end.isAfter(today)) {
                model.addAttribute("error", "memory.validation.date.future");
                model.addAttribute("memory", memory);
                
                model.addAttribute("cancelEndpoint", "/memories/" + id);
                model.addAttribute("cancelTarget", ".memory-header");
                model.addAttribute("formTarget", ".memory-header");
                return "memories/edit :: edit-memory";
            }
            
            if (end.isBefore(start)) {
                model.addAttribute("error", "memory.validation.end.date.before.start");
                model.addAttribute("memory", memory);
                
                model.addAttribute("cancelEndpoint", "/memories/" + id);
                model.addAttribute("cancelTarget", ".memory-header");
                model.addAttribute("formTarget", ".memory-header");
                return "memories/edit :: edit-memory";
            }
            
            Memory updated = memory
                    .withTitle(title.trim())
                    .withDescription(description != null ? description.trim() : null)
                    .withStartDate(start)
                    .withEndDate(end)
                    .withHeaderType(headerType)
                    .withHeaderImageUrl(headerImageUrl)
                    .withVersion(version);
            
            Memory savedMemory = memoryService.updateMemory(user, updated);
            model.addAttribute("memory", savedMemory);
            
            return "memories/view :: memory-header";

        } catch (Exception e) {
            model.addAttribute("error", "memory.validation.start.date.required");
            model.addAttribute("memory", memory);
            
            model.addAttribute("cancelEndpoint", "/memories/" + id);
            model.addAttribute("cancelTarget", ".memory-header");
            model.addAttribute("formTarget", ".memory-header");
            return "memories/edit :: edit-memory";
        }
    }

    @DeleteMapping("/{id}")
    public String deleteMemory(@AuthenticationPrincipal User user, @PathVariable Long id) {
        memoryService.deleteMemory(user, id);
        return "redirect:/memories";
    }

    @GetMapping("/{id}/blocks/select-type")
    public String selectBlockType(@AuthenticationPrincipal User user, @PathVariable Long id, @RequestParam(defaultValue = "-1") int position, Model model) {
        model.addAttribute("memoryId", id);
        model.addAttribute("position", position);
        return "memories/fragments :: block-type-selection";
    }

    @GetMapping("/fragments/empty")
    public String emptyFragment() {
        return "memories/fragments :: empty";
    }

    @PostMapping("/{id}/recalculate")
    @ResponseBody
    public String recalculateMemory(@AuthenticationPrincipal User user, @PathVariable Long id,
                                    @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                                    HttpServletResponse httpResponse) {
        memoryService.getMemoryById(user, id).orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        memoryService.recalculateMemory(user, id, timezone);
        httpResponse.setHeader("HX-Redirect", "/memories/" + id + "?timezone=" + timezone.getId());
        return "Ok";
    }

    @GetMapping("/{id}/blocks/new")
    public String newBlockForm(@AuthenticationPrincipal User user,
                               @PathVariable Long id,
                               @RequestParam String type,
                               @RequestParam(defaultValue = "-1") int position, Model model) {

        Memory memory = memoryService.getMemoryById(user, id).orElseThrow(() -> new IllegalArgumentException("Memory not found"));

        model.addAttribute("memoryId", id);
        model.addAttribute("position", position);
        model.addAttribute("blockType", type);

        switch (type) {
            case "TEXT":
                return "memories/fragments :: text-block-form";
            case "TRIP_CLUSTER":
                model.addAttribute("availableTrips", this.tripJdbcService.findByUserAndTimeOverlap(user, memory.getStartDate(), memory.getEndDate()));
                return "memories/fragments :: trip-block-form";
            case "VISIT_CLUSTER":
                model.addAttribute("availableVisits", this.processedVisitJdbcService.findByUserAndTimeOverlap(user, memory.getStartDate(), memory.getEndDate()));
                return "memories/fragments :: visit-block-form";
            case "IMAGE_GALLERY":
                boolean immichEnabled = immichIntegrationService.getIntegrationForUser(user)
                        .map(ImmichIntegration::isEnabled)
                        .orElse(false);
                model.addAttribute("immichEnabled", immichEnabled);
                return "memories/fragments :: image-gallery-block-form";
            default:
                throw new IllegalArgumentException("Unknown block type: " + type);
        }
    }

    @GetMapping("/{id}/share")
    public String shareMemoryDropdown(@AuthenticationPrincipal User user, @PathVariable Long id, Model model) {
        Memory memory = memoryService.getMemoryById(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        model.addAttribute("memory", memory);
        return "memories/fragments :: share-dropdown";
    }

    @GetMapping("/{id}/share/form")
    public String shareMemoryForm(@AuthenticationPrincipal User user, @PathVariable Long id, 
                                  @RequestParam MagicLinkAccessLevel accessLevel, Model model) {
        Memory memory = memoryService.getMemoryById(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        model.addAttribute("memory", memory);
        model.addAttribute("accessLevel", accessLevel);
        return "memories/fragments :: share-form";
    }

    @PostMapping("/{id}/share")
    public String createShareLink(@AuthenticationPrincipal User user, 
                                  @PathVariable Long id,
                                  @RequestParam MagicLinkAccessLevel accessLevel,
                                  @RequestParam(defaultValue = "30") int validDays,
                                  HttpServletRequest request,
                                  Model model) {
        Memory memory = memoryService.getMemoryById(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        String token = magicLinkTokenService.createMemoryShareToken(user, id, accessLevel, validDays);
        String baseUrl = getBaseUrl(request);
        String shareUrl = baseUrl + "/memories/" + id + "?token=" + token;
        
        model.addAttribute("shareUrl", shareUrl);
        model.addAttribute("memory", memory);
        model.addAttribute("accessLevel", accessLevel);
        return "memories/fragments :: share-result";
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);

        if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }

        url.append(contextPath);
        return url.toString();
    }
}
