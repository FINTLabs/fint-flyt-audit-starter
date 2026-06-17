package no.novari.flyt.audit.revision

import org.springframework.data.jpa.repository.JpaRepository

interface RevisedTestEntityRepository : JpaRepository<RevisedTestEntity, Long>
