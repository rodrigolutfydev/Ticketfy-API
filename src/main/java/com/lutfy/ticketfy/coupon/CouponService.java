package com.lutfy.ticketfy.coupon;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.event.Event;
import com.lutfy.ticketfy.event.EventRepository;
import com.lutfy.ticketfy.infra.exception.EventAccessDeniedException;
import com.lutfy.ticketfy.infra.exception.EventNotFoundException;
import com.lutfy.ticketfy.infra.exception.InvalidEventStateException;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.exception.TicketTypeNotFoundException;
import com.lutfy.ticketfy.tickettype.TicketTypeRepository;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CouponService {

    private static final String UNIQUE_CODE_CONSTRAINT = "uq_coupons_event_code";
    private static final String INVALID_COUPON_MESSAGE = "Coupon is invalid or unavailable";
    private static final BigDecimal MAX_PERCENT = BigDecimal.valueOf(100);

    private final CouponRepository couponRepository;
    private final CouponRedemptions redemptions;
    private final CouponAttemptLimiter limiter;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final AuditService auditService;
    private final Clock clock;

    public CouponService(CouponRepository couponRepository, CouponRedemptions redemptions,
                         CouponAttemptLimiter limiter, EventRepository eventRepository,
                         TicketTypeRepository ticketTypeRepository, AuditService auditService, Clock clock) {
        this.couponRepository = couponRepository;
        this.redemptions = redemptions;
        this.limiter = limiter;
        this.eventRepository = eventRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public CouponDetailsDTO create(UUID eventId, CouponCreationDTO dto, User authenticated) {
        var event = findManagedEvent(eventId, authenticated);
        checkNotCancelled(event);
        var settings = dto.settings();
        validate(settings);
        var coupon = new Coupon(event, CouponCodes.normalize(dto.code()), settings);
        try {
            couponRepository.saveAndFlush(coupon);
        } catch (DataIntegrityViolationException ex) {
            throw translateCodeConflict(ex);
        }
        var details = new LinkedHashMap<String, Object>();
        details.put("eventId", eventId);
        details.put("code", coupon.getCode());
        details.putAll(describe(settings));
        auditService.record(AuditAction.COUPON_CREATED, AuditTargetType.COUPON, coupon.getId(), details);
        return new CouponDetailsDTO(coupon, 0, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<CouponDetailsDTO> list(UUID eventId, User authenticated) {
        findManagedEvent(eventId, authenticated);
        var coupons = couponRepository.findByEventIdOrderByCreatedAtAscCodeAsc(eventId);
        if (coupons.isEmpty()) return List.of();
        var usage = couponRepository.findPaidUsage(coupons.stream().map(Coupon::getId).toList()).stream()
                .collect(Collectors.toMap(CouponRepository.CouponUsage::getCouponId, Function.identity()));
        return coupons.stream().map(coupon -> details(coupon, usage.get(coupon.getId()))).toList();
    }

    @Transactional
    public CouponDetailsDTO update(UUID eventId, UUID couponId, CouponUpdateDTO dto, User authenticated) {
        var event = findManagedEvent(eventId, authenticated);
        checkNotCancelled(event);
        var coupon = couponRepository.findForUpdate(couponId, eventId)
                .orElseThrow(() -> new ProblemException(ProblemType.COUPON_NOT_FOUND, "Coupon not found"));
        var settings = dto.settings();
        validate(settings);
        if (coupon.isUsed() && coupon.changesDiscount(settings)) {
            throw new ProblemException(ProblemType.COUPON_ALREADY_USED,
                    "Discount type and value cannot change after the coupon was used");
        }
        if (settings.maxUses() != null && settings.maxUses() < coupon.getUsesCount()) {
            throw new ProblemException(ProblemType.INVALID_COUPON_SETTINGS,
                    "maxUses cannot be lower than the " + coupon.getUsesCount() + " uses already made");
        }

        var before = describe(current(coupon));
        var wasActive = coupon.getActive();
        coupon.apply(settings);
        couponRepository.flush();

        var after = describe(settings);
        var changes = new LinkedHashMap<String, Object>();
        before.forEach((field, previous) -> {
            if (!field.equals("active") && !sameValue(previous, after.get(field))) {
                var change = new LinkedHashMap<String, Object>();
                change.put("from", previous);
                change.put("to", after.get(field));
                changes.put(field, change);
            }
        });
        if (!changes.isEmpty()) {
            var details = new LinkedHashMap<String, Object>();
            details.put("eventId", eventId);
            details.put("code", coupon.getCode());
            details.put("changes", changes);
            auditService.record(AuditAction.COUPON_UPDATED, AuditTargetType.COUPON, couponId, details);
        }
        if (wasActive != settings.active()) {
            auditService.record(settings.active() ? AuditAction.COUPON_REACTIVATED : AuditAction.COUPON_DEACTIVATED,
                    AuditTargetType.COUPON, couponId, Map.of("eventId", eventId, "code", coupon.getCode()));
        }
        var usage = couponRepository.findPaidUsage(List.of(couponId)).stream().findFirst().orElse(null);
        return details(coupon, usage);
    }

    @Transactional
    public void delete(UUID eventId, UUID couponId, User authenticated) {
        findManagedEvent(eventId, authenticated);
        var coupon = couponRepository.findForUpdate(couponId, eventId)
                .orElseThrow(() -> new ProblemException(ProblemType.COUPON_NOT_FOUND, "Coupon not found"));
        if (coupon.isUsed()) {
            throw new ProblemException(ProblemType.COUPON_ALREADY_USED,
                    "Coupons that were already used cannot be deleted; deactivate it instead");
        }
        couponRepository.delete(coupon);
        couponRepository.flush();
        auditService.record(AuditAction.COUPON_DELETED, AuditTargetType.COUPON, couponId,
                Map.of("eventId", eventId, "code", coupon.getCode()));
    }

    @Transactional(readOnly = true)
    public CouponPreviewDTO preview(UUID eventId, CouponPreviewRequestDTO dto, User authenticated) {
        limiter.checkAllowed(authenticated.getId());
        var subtotal = BigDecimal.ZERO;
        for (var item : dto.items()) {
            var ticketType = ticketTypeRepository
                    .findByIdAndActiveTrueAndEventActiveTrueAndEventCancelledAtIsNull(item.ticketTypeId())
                    .filter(found -> found.getEvent().getId().equals(eventId))
                    .orElseThrow(() -> new TicketTypeNotFoundException("Ticket type not found"));
            subtotal = subtotal.add(ticketType.getPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        var code = CouponCodes.normalize(dto.code());
        var coupon = Optional.ofNullable(code)
                .flatMap(normalized -> redemptions.findAvailable(eventId, normalized, clock.instant()))
                .orElseThrow(() -> rejected(authenticated));
        var discount = coupon.discountFor(subtotal);
        return new CouponPreviewDTO(coupon.code(), coupon.discountType(), coupon.discountValue(),
                subtotal, discount, subtotal.subtract(discount));
    }

    public void checkAttemptsAllowed(User authenticated) {
        limiter.checkAllowed(authenticated.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CouponRedemption redeem(UUID eventId, String rawCode, User authenticated) {
        var code = CouponCodes.normalize(rawCode);
        if (code == null) {
            throw rejected(authenticated);
        }
        return redemptions.redeem(eventId, code, clock.instant())
                .orElseThrow(() -> rejected(authenticated));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseUse(UUID couponId) {
        couponRepository.releaseUse(couponId);
    }

    private ProblemException rejected(User authenticated) {
        limiter.recordFailure(authenticated.getId());
        return new ProblemException(ProblemType.INVALID_COUPON, INVALID_COUPON_MESSAGE);
    }

    private static void validate(CouponSettings settings) {
        if (settings.discountType() == DiscountType.PERCENT && settings.discountValue().compareTo(MAX_PERCENT) > 0) {
            throw new ProblemException(ProblemType.INVALID_COUPON_SETTINGS,
                    "Percentage discounts must be between 0.01 and 100");
        }
        if (settings.startsAt() != null && settings.endsAt() != null
                && !settings.endsAt().isAfter(settings.startsAt())) {
            throw new ProblemException(ProblemType.INVALID_COUPON_SETTINGS, "endsAt must be after startsAt");
        }
    }

    private static CouponSettings current(Coupon coupon) {
        return new CouponSettings(coupon.getDiscountType(), coupon.getDiscountValue(), coupon.getMaxUses(),
                coupon.getStartsAt(), coupon.getEndsAt(), coupon.getActive());
    }

    private static Map<String, Object> describe(CouponSettings settings) {
        var values = new LinkedHashMap<String, Object>();
        values.put("discountType", settings.discountType());
        values.put("discountValue", settings.discountValue());
        values.put("maxUses", settings.maxUses());
        values.put("startsAt", settings.startsAt());
        values.put("endsAt", settings.endsAt());
        values.put("active", settings.active());
        return values;
    }

    private static boolean sameValue(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) return x.compareTo(y) == 0;
        return Objects.equals(a, b);
    }

    private static CouponDetailsDTO details(Coupon coupon, CouponRepository.CouponUsage usage) {
        return usage == null
                ? new CouponDetailsDTO(coupon, 0, BigDecimal.ZERO)
                : new CouponDetailsDTO(coupon, usage.getPaidOrders(), usage.getDiscountTotal());
    }

    private RuntimeException translateCodeConflict(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && UNIQUE_CODE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return new ProblemException(ProblemType.COUPON_CODE_ALREADY_EXISTS,
                        "A coupon with this code already exists for this event");
            }
        }
        return ex;
    }

    private Event findManagedEvent(UUID eventId, User authenticated) {
        var event = eventRepository.findByIdAndActiveTrue(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        if (authenticated.getRole() != Role.ADMIN && !event.getOrganizer().getId().equals(authenticated.getId())) {
            throw new EventAccessDeniedException("You do not own this event");
        }
        return event;
    }

    private static void checkNotCancelled(Event event) {
        if (event.isCancelled()) {
            throw new InvalidEventStateException("Event was cancelled");
        }
    }
}
