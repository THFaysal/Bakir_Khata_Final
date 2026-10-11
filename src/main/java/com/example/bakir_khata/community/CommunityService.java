package com.example.bakir_khata.community;

import com.example.bakir_khata.dto.LoanDTO;
import com.example.bakir_khata.config.CacheInvalidationService;
import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.model.Lender;
import com.example.bakir_khata.model.Loan;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.*;
import com.example.bakir_khata.repository.*;
import com.example.bakir_khata.service.*;
import com.example.bakir_khata.verification.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service @RequiredArgsConstructor
public class CommunityService {
    private final CommunityProfileRepository profiles;
    private final CachedCommunityFeed publicFeedCache;
    private final CacheInvalidationService cacheInvalidation;
    private final LoanBroadcastRepository broadcasts;
    private final LendingOfferRepository offers;
    private final LoanAgreementRepository agreements;
    private final CommunityConnectionRepository connections;
    private final SubscriptionRepository subscriptions;
    private final ModerationReportRepository reports;
    private final UserRepository users;
    private final LenderRepository lenders;
    private final LoanService loanService;
    private final NotificationService notificationService;
    private final VerificationService verification;
    private final FileStorageService files;
    private final EntityManager entityManager;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Transactional
    public CommunityProfile profile(User actor) {
        User user = verification.requireCommunity(actor);
        return profiles.findByUserId(user.getId()).orElseGet(() -> {
            CommunityProfile profile = new CommunityProfile();
            profile.setUser(user);
            String code;
            do { code = generateCode(); } while (profiles.existsByMessengerId(code));
            profile.setMessengerId(code);
            profile.setDisplayName("Member-" + code.substring(3,9));
            CommunityProfile saved = profiles.save(profile);
            cacheInvalidation.communityFeedChanged();
            return saved;
        });
    }
    private String generateCode() {
        StringBuilder sb = new StringBuilder("bk_");
        for (int i=0;i<12;i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }
    @Transactional
    public void updateProfile(User actor, String displayName, MultipartFile avatar) {
        CommunityProfile p=profile(actor);
        if (displayName==null || displayName.isBlank() || displayName.trim().length()>60)
            throw new BusinessRuleException("Display name must be 1 to 60 characters.");
        p.setDisplayName(displayName.trim());
        if (avatar!=null && !avatar.isEmpty()) {
            if (avatar.getSize()>2*1024*1024 || avatar.getContentType()==null ||
                !Set.of("image/jpeg","image/png","image/webp").contains(avatar.getContentType()))
                throw new BusinessRuleException("Avatar must be a PNG, JPG or WebP up to 2MB.");
            p.setAvatarUrl(files.store(avatar, "messenger-avatars"));
        }
        profiles.save(p);
        cacheInvalidation.communityFeedChanged();
    }

    public Subscription.Plan plan(User actor) {
        Subscription sub = subscriptions.findByUserId(actor.getId()).orElse(null);
        return sub!=null && sub.getPlan()==Subscription.Plan.PREMIUM &&
                sub.getPremiumUntil()!=null && sub.getPremiumUntil().isAfter(LocalDateTime.now())
                ? Subscription.Plan.PREMIUM : Subscription.Plan.FREE;
    }
    public int weeklyQuota(User user) { return plan(user)==Subscription.Plan.PREMIUM?20:5; }
    public int lenderQuota(User user) { return plan(user)==Subscription.Plan.PREMIUM?50:5; }
    private LocalDate weekStart() {
        return LocalDate.now(ZoneId.of("Asia/Dhaka")).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    public long usedThisWeek(User user) {
        LocalDateTime start=weekStart().atStartOfDay();
        return broadcasts.countByOwnerIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                user.getId(),start,start.plusWeeks(1));
    }
    @Transactional
    public void grantPremium(User actor, Long userId, int days) {
        if (actor.getRole()!=Role.ADMIN) throw new BusinessRuleException("Admin access required.");
        if (days<1 || days>365) throw new BusinessRuleException("Premium duration must be 1-365 days.");
        User target=users.findById(userId).orElseThrow(()->new BusinessRuleException("User not found."));
        Subscription sub=subscriptions.findByUserId(userId).orElseGet(Subscription::new);
        sub.setUser(target); sub.setPlan(Subscription.Plan.PREMIUM);
        LocalDateTime now=LocalDateTime.now();
        LocalDateTime current=sub.getPremiumUntil();
        sub.setPremiumUntil((current!=null && current.isAfter(now)?current:now).plusDays(days));
        subscriptions.save(sub);
    }
    @Transactional
    public void demoUpgrade(User actor,int days) {
        User user=verification.requireCommunity(actor);
        if (days!=30) throw new BusinessRuleException("Demo subscription duration must be 30 days.");
        Subscription sub=subscriptions.findByUserId(user.getId()).orElseGet(Subscription::new);
        sub.setUser(user); sub.setPlan(Subscription.Plan.PREMIUM);
        LocalDateTime now=LocalDateTime.now();
        LocalDateTime current=sub.getPremiumUntil();
        sub.setPremiumUntil((current!=null && current.isAfter(now)?current:now).plusDays(days));
        subscriptions.save(sub);
    }
    @Transactional
    public LoanBroadcast publish(User actor, BigDecimal amount, String purpose, String description, int durationDays) {
        User user=verification.requireCommunity(actor);
        // Serialize simultaneous quota-consuming broadcasts for the same account.
        users.lockById(user.getId()).orElseThrow(()->new BusinessRuleException("Account not found."));
        if (amount==null || amount.compareTo(new BigDecimal("1"))<0 || amount.compareTo(new BigDecimal("10000000"))>0)
            throw new BusinessRuleException("Amount must be between 1 and 10,000,000 BDT.");
        if (purpose==null || purpose.isBlank() || purpose.length()>150 ||
                description==null || description.isBlank() || description.length()>1000)
            throw new BusinessRuleException("Provide a purpose and description (max 150/1000 characters).");
        if (durationDays<1 || durationDays>365) throw new BusinessRuleException("Repayment period must be 1-365 days.");
        if (usedThisWeek(user)>=weeklyQuota(user)) throw new BusinessRuleException("Your weekly broadcast limit has been reached.");
        LoanBroadcast b=new LoanBroadcast(); b.setOwner(user);b.setAmount(amount);
        b.setPurpose(purpose.trim());b.setDescription(description.trim());b.setDurationDays(durationDays);
        b.setExpiresAt(LocalDateTime.now().plusDays(7));
        b=broadcasts.save(b);
        cacheInvalidation.communityFeedChanged();
        // Every verified account can browse the community feed; also create inbox alerts
        // for verified accounts for the small final-year demonstration population.
        for (User recipient: users.findAll()) {
            if (!recipient.getId().equals(user.getId()) && recipient.isCommunityVerified() && recipient.isEnabled())
                notificationService.notify(recipient,"COMMUNITY_BROADCAST", "New loan request: "+b.getPurpose()+" (BDT "+b.getAmount()+")", b.getId());
        }
        return b;
    }
    @Transactional(readOnly=true)
    public List<PublicBroadcastView> publicFeed(User actor) {
        verification.requireCommunity(actor); // Authorization is never cached.
        LocalDateTime now = LocalDateTime.now(); // Avoid showing expired items during the TTL.
        return publicFeedCache.openBroadcasts().stream()
                .filter(b -> b.expiresAt().isAfter(now)).toList();
    }
    @Transactional(readOnly=true)
    public List<LoanBroadcast> ownBroadcasts(User actor) {
        return broadcasts.findByOwnerIdOrderByCreatedAtDesc(actor.getId());
    }
    @Transactional
    public void close(User actor, Long id) {
        LoanBroadcast b=broadcasts.findById(id).orElseThrow(()->new BusinessRuleException("Request not found."));
        if (!b.getOwner().getId().equals(actor.getId())) throw new BusinessRuleException("Only the owner may close a request.");
        if (b.getStatus()!=LoanBroadcast.Status.OPEN) throw new BusinessRuleException("Request is not open.");
        b.setStatus(LoanBroadcast.Status.CLOSED); broadcasts.save(b);
        cacheInvalidation.communityFeedChanged();
    }
    @Transactional
    public LendingOffer offer(User actor, Long broadcastId, BigDecimal amount, String note) {
        User lender=verification.requireCommunity(actor);
        LoanBroadcast b=broadcasts.findById(broadcastId).orElseThrow(()->new BusinessRuleException("Request not found."));
        if (b.getOwner().getId().equals(lender.getId())) throw new BusinessRuleException("Cannot lend to yourself.");
        if (b.getStatus()!=LoanBroadcast.Status.OPEN || b.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new BusinessRuleException("This loan request is closed or expired.");
        if (amount==null || amount.compareTo(b.getAmount())!=0)
            throw new BusinessRuleException("For this demo, offer the full requested amount.");
        if (note==null || note.isBlank() || note.length()>600) throw new BusinessRuleException("Add a note (max 600 characters).");
        if (offers.findByBroadcastIdAndLenderId(broadcastId,lender.getId()).isPresent())
            throw new BusinessRuleException("You already responded to this request.");
        LendingOffer offer=new LendingOffer();offer.setBroadcast(b);offer.setLender(lender);
        offer.setAmount(amount);offer.setNote(note.trim());offer=offers.save(offer);
        notificationService.notify(b.getOwner(),"LENDING_OFFER","Someone offered to fund your request #"+b.getId(),b.getId());
        return offer;
    }
    @Transactional(readOnly=true)
    public List<LendingOffer> offersFor(User actor,Long broadcastId) {
        LoanBroadcast b=broadcasts.findById(broadcastId).orElseThrow(()->new BusinessRuleException("Request not found."));
        if (!b.getOwner().getId().equals(actor.getId())) throw new BusinessRuleException("Not your request.");
        return offers.findByBroadcastIdOrderByCreatedAtDesc(broadcastId);
    }
    @Transactional(readOnly=true) public List<LendingOffer> myOffers(User actor) {
        return offers.findByLenderIdOrderByCreatedAtDesc(actor.getId());
    }
    @Transactional
    public LoanAgreement accept(User actor,Long offerId) {
        User borrower=verification.requireCommunity(actor);
        users.lockById(borrower.getId()).orElseThrow(()->new BusinessRuleException("Account not found."));
        LendingOffer offer=offers.findById(offerId).orElseThrow(()->new BusinessRuleException("Offer not found."));
        LoanBroadcast b=offer.getBroadcast();
        if (!b.getOwner().getId().equals(borrower.getId())) throw new BusinessRuleException("This offer is not for you.");
        if (b.getStatus()!=LoanBroadcast.Status.OPEN || offer.getStatus()!=LendingOffer.Status.PENDING)
            throw new BusinessRuleException("Offer or broadcast is no longer open.");
        if (agreements.findByOfferId(offerId).isPresent()) throw new BusinessRuleException("Offer was already accepted.");
        boolean connected=connections.existsByBorrowerIdAndLenderIdAndActiveTrue(borrower.getId(),offer.getLender().getId());
        if (!connected && connections.countByBorrowerIdAndActiveTrue(borrower.getId())>=lenderQuota(borrower))
            throw new BusinessRuleException("Your active community lender limit is reached. Upgrade or deactivate a connection.");
        if (!connected) {
            CommunityConnection c=connections.findByBorrowerIdAndLenderId(borrower.getId(),offer.getLender().getId())
                    .orElseGet(CommunityConnection::new);
            c.setBorrower(borrower);c.setLender(offer.getLender());c.setActive(true);connections.save(c);
        }
        offer.setStatus(LendingOffer.Status.ACCEPTED);offers.save(offer);
        b.setStatus(LoanBroadcast.Status.RESERVED); broadcasts.save(b);
        cacheInvalidation.communityFeedChanged();
        LoanAgreement agreement=new LoanAgreement();agreement.setOffer(offer);
        agreement.setDueDate(LocalDate.now().plusDays(b.getDurationDays()));
        agreement=agreements.save(agreement);
        notificationService.notify(offer.getLender(),"OFFER_ACCEPTED", "Your offer #"+offer.getId()+" was accepted. Confirm the demo disbursement.",offer.getId());
        return agreement;
    }
    @Transactional
    public LoanAgreement confirmDisbursement(User actor,Long agreementId) {
        User lender=verification.requireCommunity(actor);
        LoanAgreement agreement=agreements.findById(agreementId).orElseThrow(()->new BusinessRuleException("Agreement not found."));
        LendingOffer offer=agreement.getOffer();
        if (!offer.getLender().getId().equals(lender.getId())) throw new BusinessRuleException("Only the lender can confirm disbursement.");
        LoanBroadcast b=offer.getBroadcast();
        User borrower=b.getOwner();
        // Serialize confirmations with cancellations for one borrower, then refresh the
        // agreement to detect any concurrent request that already changed its state.
        users.lockById(borrower.getId()).orElseThrow(()->new BusinessRuleException("Account not found."));
        entityManager.refresh(agreement);
        if (agreement.getStatus()!=LoanAgreement.Status.AWAITING_LENDER) throw new BusinessRuleException("Already processed.");
        if (b.getStatus()!=LoanBroadcast.Status.RESERVED) throw new BusinessRuleException("Request is no longer awaiting this disbursement.");
        Lender lenderRow=lenders.findFirstByUserIdAndLinkedUserId(borrower.getId(), lender.getId()).orElseGet(()-> {
            Lender row=new Lender();row.setUser(borrower);row.setLinkedUser(lender);
            row.setName(profiles.findByUserId(lender.getId()).map(CommunityProfile::getDisplayName).orElse("Community lender"));
            row.setPhone("PRIVATE"); row.setRelationship(Relationship.OTHER);
            row.setNotes("Explicitly linked through an accepted community lending offer.");
            return lenders.save(row);
        });
        // A loan exists only after two affirmative actions: borrower accepts, lender confirms.
        LoanDTO dto=new LoanDTO(null,lenderRow.getId(),offer.getAmount(),LocalDate.now(),agreement.getDueDate(),
                b.getPurpose(),Priority.MEDIUM,LoanTag.OTHER,PaymentType.ONE_TIME,null,null,
                "COMMUNITY DEMO AGREEMENT #"+agreement.getId()+"; disbursement simulated; no real funds moved.");
        Loan loan=loanService.saveLoan(dto,borrower);
        agreement.setLoan(loan);agreement.setStatus(LoanAgreement.Status.ACTIVE);agreement.setConfirmedAt(LocalDateTime.now());
        b.setStatus(LoanBroadcast.Status.FUNDED);
        agreements.save(agreement);broadcasts.save(b);
        cacheInvalidation.communityFeedChanged();
        for (LendingOffer other: offers.findByBroadcastIdOrderByCreatedAtDesc(b.getId())) {
            if (!other.getId().equals(offer.getId()) && other.getStatus()==LendingOffer.Status.PENDING) {
                other.setStatus(LendingOffer.Status.REJECTED);offers.save(other);
            }
        }
        notificationService.notify(borrower,"DEMO_LOAN_CREATED", "Your community loan #"+loan.getId()+" was created after demo disbursement. Download the loan receipt under My Receipts.",loan.getId());
        notificationService.notify(lender,"DEMO_LENDING_CONFIRMED", "You confirmed demo loan #"+loan.getId()+". Your matching PDF loan receipt is available under My Receipts.",loan.getId());
        return agreement;
    }
    @Transactional(readOnly=true)
    public List<LoanAgreement> pendingForBorrower(User actor) {
        return agreements.findByOfferBroadcastOwnerIdAndStatus(actor.getId(),LoanAgreement.Status.AWAITING_LENDER);
    }
    @Transactional
    public void cancelPendingAgreement(User actor,Long agreementId) {
        LoanAgreement agreement=agreements.findById(agreementId).orElseThrow(()->new BusinessRuleException("Agreement not found."));
        LendingOffer offer=agreement.getOffer();
        LoanBroadcast broadcast=offer.getBroadcast();
        if (!broadcast.getOwner().getId().equals(actor.getId()) && !offer.getLender().getId().equals(actor.getId()))
            throw new BusinessRuleException("Only the borrower or lender may cancel a pending agreement.");
        users.lockById(broadcast.getOwner().getId()).orElseThrow(()->new BusinessRuleException("User not found."));
        entityManager.refresh(agreement);
        if (agreement.getStatus()!=LoanAgreement.Status.AWAITING_LENDER)
            throw new BusinessRuleException("Only an unconfirmed agreement can be cancelled.");
        agreement.setStatus(LoanAgreement.Status.DECLINED); agreements.save(agreement);
        offer.setStatus(LendingOffer.Status.REJECTED); offers.save(offer);
        if (broadcast.getStatus()==LoanBroadcast.Status.RESERVED) {
            broadcast.setStatus(LoanBroadcast.Status.OPEN); broadcasts.save(broadcast);
            cacheInvalidation.communityFeedChanged();
        }
        Long borrowerId=broadcast.getOwner().getId();
        Long lenderId=offer.getLender().getId();
        if (!hasOutstandingCommunityAgreement(borrowerId,lenderId)) {
            connections.findByBorrowerIdAndLenderId(borrowerId,lenderId)
                    .ifPresent(c -> {c.setActive(false); connections.save(c);});
        }
        notificationService.notify(broadcast.getOwner(),"AGREEMENT_CANCELLED",
                "Agreement #"+agreement.getId()+" cancelled before disbursement.",agreement.getId());
    }

    @Transactional(readOnly=true)
    public List<LoanAgreement> pendingForLender(User actor) {
        return agreements.findByOfferLenderIdAndStatus(actor.getId(),LoanAgreement.Status.AWAITING_LENDER);
    }
    @Transactional
    public ModerationReport report(User actor,Long broadcastId,String reason) {
        User reporter=verification.requireCommunity(actor);
        LoanBroadcast b=broadcasts.findById(broadcastId).orElseThrow(()->new BusinessRuleException("Request not found."));
        if (reason==null || reason.isBlank() || reason.length()>400) throw new BusinessRuleException("Describe the issue (max 400 characters).");
        ModerationReport report=new ModerationReport();report.setReporter(reporter);report.setBroadcast(b);report.setReason(reason.trim());
        return reports.save(report);
    }
    public List<ModerationReport> openReports() { return reports.findByStatusOrderByCreatedAtDesc(ModerationReport.Status.OPEN); }
    @Transactional
    public void moderate(User actor,Long id,boolean hide) {
        if (actor.getRole()!=Role.ADMIN && actor.getRole()!=Role.COORDINATOR)
            throw new BusinessRuleException("Coordinator or Admin access required.");
        ModerationReport report=reports.findById(id).orElseThrow(()->new BusinessRuleException("Report not found."));
        if (report.getStatus()!=ModerationReport.Status.OPEN) throw new BusinessRuleException("Already handled.");
        if (hide) {
            report.getBroadcast().setStatus(LoanBroadcast.Status.HIDDEN);
            broadcasts.save(report.getBroadcast());
            cacheInvalidation.communityFeedChanged();
        }
        report.setStatus(hide?ModerationReport.Status.RESOLVED:ModerationReport.Status.DISMISSED);reports.save(report);
    }
    public Optional<CommunityProfile> findPublicProfile(Long userId) { return profiles.findByUserId(userId); }
    public Optional<CommunityProfile> findPublicId(String messengerId) { return profiles.findByMessengerId(messengerId); }
    public List<CommunityConnection> connections(User actor) { return connections.findByBorrowerIdOrLenderId(actor.getId(),actor.getId()); }
    private boolean hasOutstandingCommunityAgreement(Long borrowerId, Long lenderId) {
        for (LoanAgreement agreement: agreements.findByOfferBroadcastOwnerIdAndOfferLenderId(borrowerId,lenderId)) {
            if (agreement.getStatus()==LoanAgreement.Status.AWAITING_LENDER ||
                (agreement.getStatus()==LoanAgreement.Status.ACTIVE && agreement.getLoan()!=null &&
                 agreement.getLoan().getRemainingAmount().signum()>0)) return true;
        }
        return false;
    }
    @Transactional
    public void disconnect(User actor,Long lenderId) {
        User borrower=verification.requireCommunity(actor);
        users.lockById(borrower.getId()).orElseThrow(()->new BusinessRuleException("Account not found."));
        CommunityConnection c=connections.findByBorrowerIdAndLenderId(borrower.getId(),lenderId)
                .orElseThrow(()->new BusinessRuleException("Connection not found."));
        if (hasOutstandingCommunityAgreement(borrower.getId(),lenderId))
            throw new BusinessRuleException("Finish or cancel pending and outstanding community loans before disconnecting.");
        c.setActive(false);connections.save(c);
    }
    public boolean areConnected(Long a,Long b) { return connections.existsByBorrowerIdAndLenderIdAndActiveTrue(a,b) || connections.existsByBorrowerIdAndLenderIdAndActiveTrue(b,a); }
}
