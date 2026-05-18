package com.dedicatedcode.reitti.controller.translations;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                return new ObjectMapper().writeValueAsString(messages);
            } catch (Exception e) { return "{}"; }
        });

        // The logic is bundled with the data
        String script = """
                (function() {
                        window.I18N = window.I18N || {};
                        Object.assign(window.I18N, %s);
    
                        if (!window.t) {
                            window.t = function(key, params = []) {
                                const internalKey = "js." + key;
                                if (!window.I18N || !(internalKey in window.I18N)) {
                                    console.error('I18N Error: Key [' + internalKey + '] not found.');
                                    return '??' + internalKey + '??';
                                }
    
                                let msg = window.I18N[internalKey];
    
                                // Helper: replace standard {N} and {N,number(,integer)?} placeholders
                                const applyParams = (str) => {
                                    params.forEach((param, index) => {
                                        str = str.replace(
                                            new RegExp('\\\\{' + index + '(?:,number(?:,integer)?)?\\\\}', 'g'),
                                            param
                                        );
                                    });
                                    return str;
                                };
    
                                // 1. Handle ChoiceFormat (Plurals) with brace-depth-aware scanning
                                const choiceStart = /\\{(\\d+),choice,/g;
                                let result = '';
                                let lastIndex = 0;
                                let m;
                                while ((m = choiceStart.exec(msg)) !== null) {
                                    // Find the matching closing brace, respecting nested {...}
                                    let depth = 1;
                                    let i = choiceStart.lastIndex;
                                    while (i < msg.length && depth > 0) {
                                        const ch = msg[i];
                                        if (ch === '{') depth++;
                                        else if (ch === '}') depth--;
                                        if (depth === 0) break;
                                        i++;
                                    }
                                    if (depth !== 0) {
                                        // Unbalanced; bail out and leave the rest as-is
                                        break;
                                    }
    
                                    const paramIndex = m[1];
                                    const choicesStr = msg.substring(choiceStart.lastIndex, i);
                                    const value = parseFloat(params[paramIndex]);
    
                                    let replacement;
                                    if (isNaN(value)) {
                                        replacement = msg.substring(m.index, i + 1);
                                    } else {
                                        const choices = choicesStr.split('|');
                                        let selectedChoice = null;
                                        for (const choice of choices) {
                                            const separatorIndex = choice.search(/[#<]/);
                                            if (separatorIndex < 0) continue;
                                            const limit = parseFloat(choice.substring(0, separatorIndex));
                                            const separator = choice.charAt(separatorIndex);
                                            const text = choice.substring(separatorIndex + 1);
                                            if ((separator === '#' && value >= limit) ||
                                                (separator === '<' && value > limit)) {
                                                selectedChoice = text;
                                            }
                                        }
                                        if (selectedChoice === null) {
                                            const first = choices[0];
                                            const sepIdx = first.search(/[#<]/);
                                            selectedChoice = sepIdx >= 0 ? first.substring(sepIdx + 1) : first;
                                        }
                                        // Resolve nested {N}/{N,number,integer} inside the chosen branch
                                        replacement = applyParams(selectedChoice);
                                    }
    
                                    result += msg.substring(lastIndex, m.index) + replacement;
                                    lastIndex = i + 1;
                                    choiceStart.lastIndex = i + 1;
                                }
                                result += msg.substring(lastIndex);
                                msg = result;
    
                                // 2. Handle remaining standard params {0}, {0,number}, {0,number,integer}
                                msg = applyParams(msg);
    
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
