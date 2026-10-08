package com.lutfy.ticketfy.audit;

import com.lutfy.ticketfy.infra.exception.InvalidDateRangeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@RestController
@RequestMapping("/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditQueryRepository queries;
    private final ZoneId zone;

    public AdminAuditController(AuditQueryRepository queries,
                                @Value("${ticketfy.dashboard.time-zone}") String timeZone) {
        this.queries = queries;
        this.zone = ZoneId.of(timeZone);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<AuditEntryDTO>> find(
            @RequestParam(required = false) AuditTargetType targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException("'from' must not be after 'to'");
        }
        var start = from == null ? null : from.atStartOfDay(zone).toInstant();
        var until = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        return ResponseEntity.ok(queries.find(targetType, targetId, actorId, start, until,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())));
    }
}
