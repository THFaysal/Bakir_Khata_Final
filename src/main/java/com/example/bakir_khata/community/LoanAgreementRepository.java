package com.example.bakir_khata.community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;
public interface LoanAgreementRepository extends JpaRepository<LoanAgreement,Long> {
    Optional<LoanAgreement> findByOfferId(Long offerId);
    @EntityGraph(attributePaths={"offer", "offer.lender", "offer.broadcast", "loan"})
    Optional<LoanAgreement> findByLoan_Id(Long loanId);
    boolean existsByLoan_Id(Long loanId);
    @EntityGraph(attributePaths={"offer", "offer.broadcast", "loan"})
    List<LoanAgreement> findByOfferBroadcastOwnerIdAndOfferLenderId(Long borrowerId, Long lenderId);
    @EntityGraph(attributePaths={"offer", "offer.broadcast"})
    List<LoanAgreement> findByOfferLenderIdAndStatus(Long lenderId,LoanAgreement.Status status);
    @EntityGraph(attributePaths={"offer", "offer.broadcast"})
    List<LoanAgreement> findByOfferBroadcastOwnerIdAndStatus(Long ownerId,LoanAgreement.Status status);
}
