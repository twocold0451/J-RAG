package com.twocold.jrag.repository;

import com.twocold.jrag.domain.UserGroupMember;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserGroupMemberRepository extends CrudRepository<UserGroupMember, Long> {
    List<UserGroupMember> findByGroupId(Long groupId);
    List<UserGroupMember> findByUserId(Long userId);
    
    @Modifying
    @Query("DELETE FROM user_group_members WHERE group_id = :groupId")
    void deleteByGroupId(Long groupId);
}
