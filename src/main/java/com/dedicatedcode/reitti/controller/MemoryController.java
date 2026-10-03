package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.controller.error.ForbiddenException;
import com.dedicatedcode.reitti.controller.error.PageNotFoundException;
import com.dedicatedcode.reitti.dto.TripDTO;
import com.dedicatedcode.reitti.dto.VisitDTO;
import com.dedicatedcode.reitti.model.integration.ImmichIntegration;
import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.service.*;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/memories")
public class MemoryController {
    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);
    private static final Set<MagicLinkAccessLevel> MEMORY_SHARE_LEVELS = Set.of(MagicLinkAccessLevel.MEMORY_VIEW_ONLY, MagicLinkAccessLevel.MEMORY_EDIT_ACCESS);

    private final MemoryService memoryService;
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final ImmichIntegrationService immichIntegrationService;
    private final MagicLinkTokenService magicLinkTokenService;
    private final I18nService i18n;
    private final ContextPathHolder contextPathHolder;
    public MemoryController(MemoryService memoryService,
                            TripJdbcService tripJdbcService,
                            ProcessedVisitJdbcService processedVisitJdbcService,
                            ImmichIntegrationService immichIntegrationService,
                            MagicLinkTokenService magicLinkTokenService,
                            I18nService i18n,
                            ContextPathHolder contextPathHolder) {
        this.memoryService = memoryService;
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.immichIntegrationService = immichIntegrationService;
        this.magicLinkTokenService = magicLinkTokenService;
        this.i18n = i18n;
        this.contextPathHolder = contextPathHolder;
    }

    @GetMapping
    public String get(@AuthenticationPrincipal User user) {
        if (user instanceof TokenUser) {
            throw new ForbiddenException("Not allowed");
        }
        if (user != null && user.getUserType() == UserType.LIVE_DATA_ONLY) {
            return "redirect:/";
        }
        return "memories/list";
    }

    @GetMapping("/years-navigation")
    public String listMemories(@AuthenticationPrincipal User user, Model model) {
        requireAccountUser(user);
        model.addAttribute("years", memoryService.getAvailableYears(user));
        return "memories/fragments :: years-navigation";
    }

    @GetMapping("/all")
    public String getAll(@AuthenticationPrincipal User user,
                         @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                         @RequestParam(defaultValue = "startDate") String sortBy,
                         @RequestParam(defaultValue = "desc") String sortOrder,
                         Model model) {
        requireAccountUser(user);
        model.addAttribute("memories", this.memoryService.getMemoriesForUser(user, sortBy, sortOrder).stream().map(m -> {
            String endDateLocal = m.getEndDate() != null ? m.getEndDate().toString() : Instant.now().toString();

            String rawLocationUrl = "/api/v1/raw-location-points?startDate=" + m.getStartDate() + "&endDate=" + endDateLocal;
            return new MemoryOverviewDTO(new MemoryDTO(m, timezone), rawLocationUrl);
        }).toList());
        model.addAttribute("year", "all");
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortOrder", sortOrder);
        return "memories/fragments :: memories-list";
    }

    @GetMapping("/year/{year}")
    public String getYear(@AuthenticationPrincipal User user,
                          @PathVariable int year,
                          @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                          @RequestParam(defaultValue = "startDate") String sortBy,
                          @RequestParam(defaultValue = "desc") String sortOrder,
                          Model model) {
        requireAccountUser(user);
        model.addAttribute("memories", this.memoryService.getMemoriesForUserAndYear(user, year, sortBy, sortOrder)
                .stream().map(m -> {
                    String startDateLocal = m.getStartDate().toString();
                    String endDateLocal = m.getEndDate() != null ? m.getEndDate().toString() : Instant.now().toString();

                    String rawLocationUrl = "/api/v1/raw-location-points?startDate=" + startDateLocal + "&endDate=" + endDateLocal;
                    return new MemoryOverviewDTO(new MemoryDTO(m, timezone), rawLocationUrl);
                }).toList());
        model.addAttribute("year", year);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortOrder", sortOrder);
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

        model.addAttribute("memory", new MemoryDTO(memory, timezone));

        List<MemoryBlockPart> blocks = memoryService.getBlockPartsForMemory(user, id, timezone);
        model.addAttribute("blocks", blocks);

        Instant startDate = memory.getStartDate();
        Instant endDate = memory.getEndDate() != null ? memory.getEndDate() : Instant.now();
        String streamUrl = String.format("/api/v2/locations/stream/%d?start=%s&end=%s&timezone=%s", user.getId(), startDate, endDate, "UTC");
        String metaDataUrl = String.format("/api/v2/locations/metadata/%d?start=%s&end=%s&timezone=%s", user.getId(), startDate, endDate, "UTC");
        model.addAttribute("streamUrl", streamUrl);
        model.addAttribute("metaDataUrl", metaDataUrl);
        model.addAttribute("canEdit", canEdit(memory, user));
        model.addAttribute("isOwner", isOwner(memory, user));
        return "memories/view";
    }

    @GetMapping("/new")
    public String newMemoryForm(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String year,
            Model model) {
        requireAccountUser(user);
        model.addAttribute("startDate", startDate);
        model.addAttribute("startTime", startTime);
        model.addAttribute("endDate", endDate);
        model.addAttribute("endTime", endTime);
        model.addAttribute("year", year);
        model.addAttribute("openEnded", false);
        return "memories/new :: memory-form";
    }

    @PostMapping
    public String createMemory(
            @AuthenticationPrincipal User user,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam LocalTime startTime,
            @RequestParam(required = false) LocalTime endTime,
            @RequestParam(required = false) String year,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
            @RequestParam(required = false, defaultValue = "MAP") HeaderType headerType,
            @RequestParam(required = false) boolean openEnded,
            @RequestParam(required = false) String headerImageUrl,
            Model model,
            HttpServletResponse response) {

        if (title == null || title.trim().isEmpty()) {
            model.addAttribute("error", i18n.translate("memory.validation.title.required"));
            model.addAttribute("title", title);
            model.addAttribute("description", description);
            model.addAttribute("startDate", startDate);
            model.addAttribute("startTime", startTime);
            model.addAttribute("endDate", endDate);
            model.addAttribute("endTime", endTime);
            model.addAttribute("openEnded", openEnded);
            return "memories/new :: memory-form";
        }
        
        try {
            Instant start = LocalDateTime.of(startDate, startTime).atZone(timezone).toInstant();
            Instant end = openEnded ? null : LocalDateTime.of(endDate, endTime).atZone(timezone).toInstant();

            // Validate end date is not before start date
            if (end != null && end.isBefore(start)) {
                model.addAttribute("error", i18n.translate("memory.validation.end.date.before.start"));
                model.addAttribute("title", title);
                model.addAttribute("description", description);
                model.addAttribute("startDate", startDate);
                model.addAttribute("startTime", startTime);
                model.addAttribute("endDate", endDate);
                model.addAttribute("endTime", endTime);
                model.addAttribute("year", year);
                model.addAttribute("openEnded", openEnded);

                return "memories/new :: memory-form";
            }
            
            Memory memory = new Memory(
                    title.trim(),
                    description != null ? description.trim() : null,
                    start,
                    end,
                    headerType,
                    headerImageUrl
            );
            
            Memory created = memoryService.createMemory(user, memory);
            this.memoryService.recalculateMemory(user, created.getId(), timezone);
            response.setHeader("HX-Redirect", contextPathHolder.getContextPath() + "/memories/" + created.getId() + "?timezone=" + timezone.getId());
            return "memories/fragments :: empty";

        } catch (Exception e) {
            log.error("Error creating memory", e);
            model.addAttribute("error", i18n.translate("memory.creation.error", e.getMessage()));
            model.addAttribute("title", title);
            model.addAttribute("description", description);
            model.addAttribute("startDate", startDate);
            model.addAttribute("startTime", startTime);
            model.addAttribute("endDate", endDate);
            model.addAttribute("endTime", endTime);
            model.addAttribute("year", year);
            model.addAttribute("openEnded", openEnded);

            return "memories/new :: memory-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editMemoryForm(@AuthenticationPrincipal User user,
                                 @PathVariable Long id,
                                 @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                                 Model model) {
        Memory memory = requireEditableMemory(user, id);
        model.addAttribute("memory", new MemoryDTO(memory, timezone));
        model.addAttribute("startDate", memory.getStartDate().atZone(timezone).toLocalDate());
        model.addAttribute("startTime", memory.getStartDate().atZone(timezone).toLocalTime().truncatedTo(ChronoUnit.SECONDS));
        Instant endDate = memory.getEndDate() != null ? memory.getEndDate() : Instant.now();
        model.addAttribute("openEnded", memory.getEndDate() == null);
        model.addAttribute("endDate", endDate.atZone(timezone).toLocalDate());
        model.addAttribute("endTime", endDate.atZone(timezone).toLocalTime().truncatedTo(ChronoUnit.SECONDS));
        return "memories/edit :: edit-memory";
    }

    @PostMapping("/{id}")
    public String updateMemory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam LocalTime startTime,
            @RequestParam(required = false) LocalTime endTime,
            @RequestParam Long version,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
            @RequestParam(required = false, defaultValue = "MAP") HeaderType headerType,
            @RequestParam(required = false) boolean openEnded,
            @RequestParam(required = false) String headerImageUrl,
            Model model) {
        
        Memory memory = requireEditableMemory(user, id);

        model.addAttribute("isOwner", isOwner(memory, user));
        model.addAttribute("canEdit", canEdit(memory, user));

        // Add validation similar to create method
        if (title == null || title.trim().isEmpty()) {
            model.addAttribute("error", i18n.translate("memory.validation.title.required"));
            model.addAttribute("memory", new MemoryDTO(memory, timezone));
            model.addAttribute("openEnded", openEnded);
            
            model.addAttribute("cancelEndpoint", "/memories/" + id);
            model.addAttribute("cancelTarget", ".memory-header");
            model.addAttribute("formTarget", ".memory-header");
            return "memories/edit :: edit-memory";
        }
        
        try {
            Instant start = LocalDateTime.of(startDate, startTime).atZone(timezone).toInstant();
            Instant end = openEnded ? null : LocalDateTime.of(endDate, endTime).atZone(timezone).toInstant();

            if (end != null && end.isBefore(start)) {
                model.addAttribute("error", i18n.translate("memory.validation.end.date.before.start"));
                model.addAttribute("memory", new MemoryDTO(memory, timezone));
                model.addAttribute("openEnded", openEnded);
                
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
            model.addAttribute("memory", new MemoryDTO(savedMemory, timezone));
            
            return "memories/view :: memory-header";

        } catch (Exception e) {
            model.addAttribute("error", i18n.translate("memory.validation.start.date.required"));
            model.addAttribute("memory", new MemoryDTO(memory, timezone));
            model.addAttribute("openEnded", openEnded);
            
            model.addAttribute("cancelEndpoint", "/memories/" + id);
            model.addAttribute("cancelTarget", ".memory-header");
            model.addAttribute("formTarget", ".memory-header");
            return "memories/edit :: edit-memory";
        }
    }

    @DeleteMapping("/{id}")
    public String deleteMemory(@AuthenticationPrincipal User user, @PathVariable Long id, HttpServletResponse response) {
        Memory memory = requireMemory(user, id);

        if (!isOwner(memory, user)) {
            throw new ForbiddenException("You are not allowed to delete this memory");
        }
        memoryService.deleteMemory(user, id);
        response.setHeader("HX-Redirect", contextPathHolder.getContextPath() + "/memories");
        return null; //return null to signal that we do not want to render a view
    }

    @GetMapping("/{id}/blocks/select-type")
    public String selectBlockType(@AuthenticationPrincipal User user, @PathVariable Long id, @RequestParam(defaultValue = "-1") int position, Model model) {
        requireEditableMemory(user, id);
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
        Memory memory = requireMemory(user, id);
        if (!isOwner(memory, user)) {
            throw new ForbiddenException("You are not allowed execute this action. Only the owner of the memory can do this.");
        }
        memoryService.recalculateMemory(user, id, timezone);
        httpResponse.setHeader("HX-Redirect", contextPathHolder.getContextPath() + "/memories/" + id + "?timezone=" + timezone.getId());
        return "Ok";
    }

    @GetMapping("/{id}/blocks/new")
    public String newBlockForm(@AuthenticationPrincipal User user,
                               @PathVariable Long id,
                               @RequestParam String type,
                               @RequestParam(defaultValue = "-1") int position,
                               @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                               Model model) {

        Memory memory = requireEditableMemory(user, id);

        model.addAttribute("memoryId", id);
        model.addAttribute("position", position);
        model.addAttribute("blockType", type);

        model.addAttribute("isOwner", isOwner(memory, user));
        model.addAttribute("canEdit", canEdit(memory, user));

        Instant startDate = memory.getStartDate();
        Instant endDate = memory.getEndDate() != null ? memory.getEndDate() : Instant.now();

        switch (type) {
            case "TEXT":
                return "memories/fragments :: text-block-form";
            case "TRIP_CLUSTER":
                model.addAttribute("availableTrips", this.tripJdbcService.findByUserAndTimeOverlap(user, startDate, endDate)
                        .stream().map(t -> TripDTO.create(t, timezone))
                        .toList());
                return "memories/fragments :: trip-block-form";
            case "VISIT_CLUSTER":
                model.addAttribute("availableVisits", this.processedVisitJdbcService.findByUserAndTimeOverlap(user, startDate, endDate)
                        .stream().map(v -> VisitDTO.create(v, timezone))
                        .toList());
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
    public String shareMemoryOverlay(@AuthenticationPrincipal User user, @PathVariable Long id, Model model) {
        Memory memory = requireOwnedMemory(user, id);
        
        model.addAttribute("memory", memory);
        return "memories/fragments :: share-overlay";
    }

    @GetMapping("/{id}/share/form")
    public String shareMemoryForm(@AuthenticationPrincipal User user, @PathVariable Long id, 
                                  @RequestParam MagicLinkAccessLevel accessLevel, Model model) {
        Memory memory = requireOwnedMemory(user, id);
        requireMemoryShareLevel(accessLevel);
        
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
        Memory memory = requireOwnedMemory(user, id);
        requireMemoryShareLevel(accessLevel);
        if (validDays < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "validDays must not be negative");
        }
        
        String token = magicLinkTokenService.createMemoryShareToken(user, id, accessLevel, validDays);
        String baseUrl = RequestHelper.getBaseUrl(request);
        String shareUrl = baseUrl + "/memories/" + id + "?mt=" + token;
        
        model.addAttribute("shareUrl", shareUrl);
        model.addAttribute("memory", memory);
        model.addAttribute("accessLevel", accessLevel);
        return "memories/fragments :: share-result";
    }

    private Memory requireMemory(User user, Long id) {
        return memoryService.getMemoryById(user, id)
                .orElseThrow(() -> new PageNotFoundException("Memory not found"));
    }

    private Memory requireEditableMemory(User user, Long id) {
        Memory memory = requireMemory(user, id);
        if (!canEdit(memory, user)) {
            throw new ForbiddenException("You are not allowed to edit this memory");
        }
        return memory;
    }

    private Memory requireOwnedMemory(User user, Long id) {
        Memory memory = requireMemory(user, id);
        if (!isOwner(memory, user)) {
            throw new ForbiddenException("Only the owner of the memory can share it");
        }
        return memory;
    }

    private static void requireMemoryShareLevel(MagicLinkAccessLevel accessLevel) {
        if (!MEMORY_SHARE_LEVELS.contains(accessLevel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported access level for a memory share");
        }
    }

    // A magic link only ever grants access to its single memory, never to the owner's memory overview.
    private static void requireAccountUser(User user) {
        if (user == null || user instanceof TokenUser) {
            throw new ForbiddenException("Not allowed");
        }
    }

    private boolean isOwner(Memory memory, User user) {
        return memoryService.isOwner(memory, user);
    }

    private boolean canEdit(Memory memory, User user) {
        return memoryService.canEdit(memory, user);
    }
}
