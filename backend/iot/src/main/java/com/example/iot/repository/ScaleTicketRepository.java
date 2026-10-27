package com.example.iot.repository;

import java.util.Optional;
import com.example.iot.entity.ScaleTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface ScaleTicketRepository extends JpaRepository<ScaleTicket, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ScaleTicket t where t.id = :id")
    Optional<ScaleTicket> lockById(String id);
}
