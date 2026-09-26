package com.househelper.repository;

import com.househelper.model.SystemEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemEventRepository extends JpaRepository<SystemEvent, Long> {

    List<SystemEvent> findAllByOrderByCreatedAtDesc();
}
