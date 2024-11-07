import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.math.BigDecimal;
import java.util.Optional;

@RestController
@RequestMapping("/bank")
public class BankAccountController {

    @Autowired
    private BankAccountService bankAccountService;

    @PostMapping("/withdraw")
    public ResponseEntity<String> withdraw(@RequestBody WithdrawalRequest request) {
        try {
            bankAccountService.withdraw(request.getAccountId(), request.getAmount());
            return ResponseEntity.ok("Withdrawal successful");
        } catch (InsufficientFundsException e) {
            return ResponseEntity.badRequest().body("Insufficient funds for withdrawal");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Withdrawal failed due to an error");
        }
    }
}

@Service
public class BankAccountService {

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private SnsPublisher snsPublisher;

    public void withdraw(Long accountId, BigDecimal amount) throws InsufficientFundsException {
        // Check current balance
        BigDecimal currentBalance = bankAccountRepository.getBalance(accountId);
        
        if (currentBalance == null || currentBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Not enough funds for withdrawal");
        }

        // Update balance
        bankAccountRepository.updateBalance(accountId, currentBalance.subtract(amount));

        // Publish withdrawal event
        WithdrawalEvent event = new WithdrawalEvent(amount, accountId, "SUCCESSFUL");
        snsPublisher.publishEvent(event);
    }
}

@Repository
public class BankAccountRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public BigDecimal getBalance(Long accountId) {
        String sql = "SELECT balance FROM accounts WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, new Object[]{accountId}, BigDecimal.class);
    }

    public void updateBalance(Long accountId, BigDecimal newBalance) {
        String sql = "UPDATE accounts SET balance = ? WHERE id = ?";
        jdbcTemplate.update(sql, newBalance, accountId);
    }
}

@Service
public class SnsPublisher {
    
    private final SnsClient snsClient;
    private final String snsTopicArn;

    @Autowired
    public SnsPublisher(SnsClient snsClient, @Value("${sns.topic.arn}") String snsTopicArn) {
        this.snsClient = snsClient;
        this.snsTopicArn = snsTopicArn;
    }

    public void publishEvent(WithdrawalEvent event) {
        String eventJson = event.toJson(); // Assume JSON conversion is done properly
        PublishRequest publishRequest = PublishRequest.builder()
                .message(eventJson)
                .topicArn(snsTopicArn)
                .build();
        PublishResponse publishResponse = snsClient.publish(publishRequest);
    }
}


public class InsufficientFundsException extends Exception {
    public InsufficientFundsException(String message) {
        super(message);
    }
}

public class WithdrawalRequest {
    private Long accountId;
    private BigDecimal amount;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}

public class WithdrawalEvent {
    private BigDecimal amount;
    private Long accountId;
    private String status;

    public WithdrawalEvent(BigDecimal amount, Long accountId, String status) {
        this.amount = amount;
        this.accountId = accountId;
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getStatus() {
        return status;
    }

    public String toJson() {
        return String.format("{\"amount\":\"%s\",\"accountId\":%d,\"status\":\"%s\"}", amount, accountId, status);
    }
}
