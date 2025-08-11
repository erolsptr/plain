package com.planit.repository;
import java.util.Set;
import com.planit.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // "SELECT * FROM users WHERE email = ?" sorgusunu çalıştırır.
    Optional<User> findByEmail(String email);
    Optional<User> findByName(String name);
    Set<User> findByNameIn(Set<String> names); 

}