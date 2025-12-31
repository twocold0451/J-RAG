package com.twocold.jrag.repository;

import com.twocold.jrag.domain.UserGroup;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserGroupRepository extends CrudRepository<UserGroup, Long> {
    Optional<UserGroup> findByName(String name);
}
