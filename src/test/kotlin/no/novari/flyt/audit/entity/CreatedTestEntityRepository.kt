package no.novari.flyt.audit.entity

import org.springframework.data.jpa.repository.JpaRepository

interface CreatedTestEntityRepository : JpaRepository<CreatedTestEntity, Long>
