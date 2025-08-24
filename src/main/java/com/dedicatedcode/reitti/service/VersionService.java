package com.dedicatedcode.reitti.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Service
public class VersionService {
    private static final Logger log = LoggerFactory.getLogger(VersionService.class);
    private final Properties gitProperties = new Properties();
    private final MessageSource messageSource;
    public VersionService(MessageSource messageSource) {
        this.messageSource = messageSource;
        loadGitProperties();
    }

    public String getVersion() {
        String property = gitProperties.getProperty("git.tags");
        if (!StringUtils.hasText(property)) {
            property = "development";
        }
        return property;
    }

    private void loadGitProperties() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("git.properties")) {
            if (is != null) {
                this.gitProperties.load(is);
                log.info("git.properties loaded successfully.");
            } else {
                log.warn("git.properties not found on classpath. About section may not display Git information.");
            }
        } catch (IOException e) {
            log.error("Failed to load git.properties", e);
        }
    }

    public String getCommitDetails() {
        String commitId = gitProperties.getProperty("git.commit.id.abbrev", getMessage("about.not.available"));
        String commitTime = gitProperties.getProperty("git.commit.time");

        StringBuilder commitDetails = new StringBuilder();
        if (!commitId.equals(getMessage("about.not.available"))) {
            commitDetails.append(commitId);
            if (commitTime != null && !commitTime.isEmpty()) {
                commitDetails.append(" (").append(commitTime);
                commitDetails.append(")");
            }
        } else {
            commitDetails.append(getMessage("about.not.available"));
        }
        return commitDetails.toString();
    }

    public String getBuildTime() {
       return gitProperties.getProperty("git.build.time", getMessage("about.not.available"));
    }


    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
