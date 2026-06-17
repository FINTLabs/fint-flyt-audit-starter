package no.novari.flyt.audit.actor

import org.springframework.data.jpa.repository.JpaRepository

interface ActorHolderRepository : JpaRepository<ActorHolderEntity, Long>
