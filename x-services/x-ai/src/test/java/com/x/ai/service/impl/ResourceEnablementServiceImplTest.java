package com.x.ai.service.impl;

import com.x.ai.common.enums.SkillScope;
import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.db.entity.AgentDisabledResource;
import com.x.ai.db.service.AgentDisabledResourceIService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourceEnablementServiceImplTest {

    @Test
    void sameResourcePreferencesAreIndependentForEachUser() {
        AgentDisabledResourceIService resources = mock(AgentDisabledResourceIService.class);
        when(resources.saveOrUpdate(any(AgentDisabledResource.class))).thenReturn(true);
        ResourceEnablementServiceImpl service = new ResourceEnablementServiceImpl(resources);
        String key = ResourceEnablement.skillKey(SkillScope.PUBLIC, "example");

        service.setEnabled("42", key, false);
        service.setEnabled("43", key, false);

        ArgumentCaptor<AgentDisabledResource> records = ArgumentCaptor.forClass(AgentDisabledResource.class);
        verify(resources, times(2)).saveOrUpdate(records.capture());
        AgentDisabledResource first = records.getAllValues().getFirst();
        AgentDisabledResource second = records.getAllValues().getLast();
        assertEquals("42", first.getUserId());
        assertEquals("43", second.getUserId());
        assertNotEquals(first.getId(), second.getId());

        service.setEnabled("42", key, true);
        verify(resources).removeById(first.getId());
    }

    @Test
    void snapshotIsImmutableAndKeepsSkillScopesIndependent() {
        Set<String> disabled = new HashSet<>(Set.of(ResourceEnablement.skillKey(SkillScope.USER, "example")));
        ResourceEnablement snapshot = new ResourceEnablement(disabled);
        disabled.clear();

        assertFalse(snapshot.isSkillEnabled(SkillScope.USER, "example"));
        assertTrue(snapshot.isSkillEnabled(SkillScope.PUBLIC, "example"));
        assertTrue(snapshot.isSkillEnabled(SkillScope.SHARED, "example"));
        assertTrue(snapshot.isMcpEnabled(7L));
    }
}
