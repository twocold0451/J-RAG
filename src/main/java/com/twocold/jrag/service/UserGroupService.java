package com.twocold.jrag.service;

import com.twocold.jrag.api.dto.UserGroupCreateRequest;
import com.twocold.jrag.api.dto.UserGroupDto;
import com.twocold.jrag.domain.UserGroup;
import com.twocold.jrag.domain.UserGroupMember;
import com.twocold.jrag.repository.TemplateRepository;
import com.twocold.jrag.repository.UserGroupMemberRepository;
import com.twocold.jrag.repository.UserGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final UserGroupRepository userGroupRepository;
    private final UserGroupMemberRepository userGroupMemberRepository;
    private final TemplateRepository templateRepository; // To count templates if possible, or just ignore for now

    @Transactional(readOnly = true)
    public List<UserGroupDto> getAllGroups() {
        return StreamSupport.stream(userGroupRepository.findAll().spliterator(), false)
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserGroupDto createGroup(UserGroupCreateRequest request) {
        UserGroup group = new UserGroup();
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setCreatedAt(OffsetDateTime.now());
        group.setUpdatedAt(OffsetDateTime.now());
        
        UserGroup savedGroup = userGroupRepository.save(group);
        
        if (request.getUserIds() != null && !request.getUserIds().isEmpty()) {
            for (Long userId : request.getUserIds()) {
                UserGroupMember member = new UserGroupMember();
                member.setGroupId(savedGroup.getId());
                member.setUserId(userId);
                member.setCreatedAt(OffsetDateTime.now());
                userGroupMemberRepository.save(member);
            }
        }
        
        return mapToDto(savedGroup);
    }

    @Transactional
    public UserGroupDto updateGroup(Long groupId, UserGroupCreateRequest request) {
        UserGroup group = userGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setUpdatedAt(OffsetDateTime.now());
        UserGroup savedGroup = userGroupRepository.save(group);
        
        // Update members - simple strategy: delete all and re-add
        userGroupMemberRepository.deleteByGroupId(groupId);
        
        if (request.getUserIds() != null) {
            for (Long userId : request.getUserIds()) {
                UserGroupMember member = new UserGroupMember();
                member.setGroupId(groupId);
                member.setUserId(userId);
                member.setCreatedAt(OffsetDateTime.now());
                userGroupMemberRepository.save(member);
            }
        }
        
        return mapToDto(savedGroup);
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        userGroupMemberRepository.deleteByGroupId(groupId);
        userGroupRepository.deleteById(groupId);
    }
    
    public List<Long> getGroupMemberIds(Long groupId) {
        return userGroupMemberRepository.findByGroupId(groupId).stream()
                .map(UserGroupMember::getUserId)
                .collect(Collectors.toList());
    }

    private UserGroupDto mapToDto(UserGroup group) {
        UserGroupDto dto = new UserGroupDto();
        dto.setId(group.getId());
        dto.setName(group.getName());
        dto.setDescription(group.getDescription());
        dto.setCreatedAt(group.getCreatedAt());
        
        // Count members
        List<UserGroupMember> members = userGroupMemberRepository.findByGroupId(group.getId());
        dto.setMemberCount(members.size());
        
        // Count templates - complex, maybe skip or implement later
        dto.setTemplateCount(0); 
        
        return dto;
    }
}
