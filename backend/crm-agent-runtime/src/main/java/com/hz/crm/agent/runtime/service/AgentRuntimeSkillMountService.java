package com.hz.crm.agent.runtime.service;

import com.hz.crm.agent.runtime.domain.AgentSkillEntity;
import com.hz.crm.common.exception.BusinessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntimeSkillMountService {

    public Path materialize(Path agentWorkspace, List<AgentSkillEntity> skills) {
        Path skillRoot = agentWorkspace.resolve("skills");
        try {
            Files.createDirectories(skillRoot);
            for (AgentSkillEntity skill : safeSkills(skills)) {
                if (skill.getContent() == null || skill.getContent().trim().length() == 0) {
                    continue;
                }
                Path skillDir = skillRoot.resolve(safeSkillKey(skill.getSkillKey()));
                Files.createDirectories(skillDir);
                Files.write(
                        skillDir.resolve("SKILL.md"),
                        skill.getContent().getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BusinessException("AGENT_SKILL_002", "Skill挂载失败");
        }
        return skillRoot;
    }

    private List<AgentSkillEntity> safeSkills(List<AgentSkillEntity> skills) {
        if (skills == null) {
            return new ArrayList<AgentSkillEntity>();
        }
        return skills;
    }

    private String safeSkillKey(String value) {
        if (value == null || value.trim().length() == 0) {
            return "default";
        }
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
