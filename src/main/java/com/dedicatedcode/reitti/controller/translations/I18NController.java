package com.dedicatedcode.reitti.controller.translations;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class I18NController {
    private final MessageSource messageSource;
    private final Map<Locale, String> cache = new ConcurrentHashMap<>();

    public I18NController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @GetMapping(value = "/js/messages.js", produces = "application/javascript")
    public ResponseEntity<?> getMessages(Locale locale, @RequestParam("v") String version) {

        String json = cache.computeIfAbsent(locale, loc -> {
            ResourceBundle bundle = ResourceBundle.getBundle("messages", loc);
            Map<String, String> messages = new HashMap<>();

            for (String key : bundle.keySet()) {
                if (key.startsWith("js.")) {
                    messages.put(key, messageSource.getMessage(key, null, loc));
                }
            }
            try {
                return new JsonMapper().writeValueAsString(messages);
            } catch (Exception e) { return "{}"; }
        });

        // The logic is bundled with the data
        String script = """
        (function() {
            window.I18N = window.I18N || {};
            // Merge new messages into the existing global object
            Object.assign(window.I18N, %s);

            if (!window.t) {
                window.t = function(key, params = []) {
                    const internalKey = "js." + key; // Automatically adds the prefix
                    if (!window.I18N || !(internalKey in window.I18N)) {
                        console.error('I18N Error: Key [' + internalKey + '] not found.');
                        return '??' + internalKey + '??';
                    }

                    let msg = window.I18N[internalKey];

                    // 1. Handle ChoiceFormat (Plurals)
                    const choiceRegex = /\\{(\\d+),choice,([^}]+)\\}/g;
                    msg = msg.replace(choiceRegex, (match, paramIndex, choicesStr) => {
                        const value = parseFloat(params[paramIndex]);
                        if (isNaN(value)) return match;
                        const choices = choicesStr.split('|');
                        let selectedChoice = "";
                        for (const choice of choices) {
                            const separatorIndex = choice.search(/[#<]/);
                            const limit = parseFloat(choice.substring(0, separatorIndex));
                            const separator = choice.charAt(separatorIndex);
                            const text = choice.substring(separatorIndex + 1);
                            if ((separator === '#' && value >= limit) || (separator === '<' && value > limit)) {
                                selectedChoice = text;
                            }
                        }
                        return selectedChoice || choices[0].substring(choices[0].search(/[#<]/) + 1);
                    });

                    // 2. Handle standard params {0}
                    params.forEach((param, index) => {
                        msg = msg.replace(new RegExp('\\\\{' + index + '(,number,integer|,number)?\\\\}', 'g'), param);
                    });

                    return msg;
                };
            }
        })();
        """.formatted(json);

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();
        if ("development".equalsIgnoreCase(version)) {
            responseBuilder
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0");
        } else {
            responseBuilder.header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");
        }
        return responseBuilder.body(script);

    }
}
