package hello.springtx.propagation;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

@Slf4j
@SpringBootTest
public class BasicTxTest {
    @Autowired
    PlatformTransactionManager platformTransactionManager;

    @TestConfiguration
    static class Config {
        /**
         * @TestConfiguration: 해당 테스트에서 필요한 스프링 설정을 추가로 할 수 있다.
         * DataSourceTransactionManager 를 스프링 빈으로 등록했다. 이후 트랜잭션 매니저인 PlatformTransactionManager 를 주입 받으면
         * 방금 등록한 DataSourceTransactionManager 가 주입된다.
         */
        @Bean
        public PlatformTransactionManager platformTransactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @Test
    void commit() {
        log.info("트랜잭션 시작");
        TransactionStatus transactionStatus = platformTransactionManager.getTransaction(new DefaultTransactionAttribute());
        log.info("트랜잭션 커밋 시작");
        platformTransactionManager.commit(transactionStatus);
        log.info("트랜잭션 커밋 완료");
    }

    @Test
    void rollback() {
        log.info("트랜잭션 시작");
        TransactionStatus transactionStatus = platformTransactionManager.getTransaction(new DefaultTransactionAttribute());
        log.info("트랜잭션 롤백 시작");
        platformTransactionManager.rollback(transactionStatus);
        log.info("트랜잭션 롤백 완료");
    }

    @Test
    void double_commit() {
        /**
         * - 로그를 보면 트랜잭션1과 트랜잭션2가 같은 conn0 커넥션을 사용중인데 이것은 중간에 커넥션 풀 때문에 그런 것이다.
         *   트랜잭션1은 conn0 커넥션을 모두 사용하고 커넥션 풀에 반납까지 완료하고, 이후에 트랜잭션2가 conn0 를 커 넥션 풀에서 획득한 것이다.
         *   따라서 둘은 완전히 다른 커넥션으로 인지하는 것이 맞다.
         *
         * - 히카리 커넥션 풀에서 커넥션을 획득하면 실제 커넥션을 그대로 반환하는 것이 아니라 내부 관리를 위해 히카리 프록시 커넥션이라는 객체를 생성해서 반환한다.
         *   물론 내부에는 실제 커넥션이 포함되어 있기 때문에 이 객체의 주소를 확인하면 커넥 션 풀에서 획득한 커넥션을 구분 할 수 있다.
         */
        log.info("트랜잭션1 시작");
        TransactionStatus transactionStatus1 = platformTransactionManager.getTransaction(new DefaultTransactionAttribute());
        log.info("트랜잭션1 커밋");
        platformTransactionManager.commit(transactionStatus1);
        log.info("트랜잭션2 시작");
        TransactionStatus transactionStatus2 = platformTransactionManager.getTransaction(new DefaultTransactionAttribute());
        log.info("트랜잭션2 커밋");
        platformTransactionManager.commit(transactionStatus2);
    }

    @Test
    void double_commit_rollback() {
        /**
         * 두 서로 다른 트랜잭션은 서로 다른 connection 을 사용하기 때문에 (커밋 / 롤백)이 구분 된다.
         */
        log.info("트랜잭션1 시작");
        TransactionStatus transactionStatus1 = platformTransactionManager.getTransaction(new DefaultTransactionAttribute());
        log.info("트랜잭션1 커밋");
        platformTransactionManager.commit(transactionStatus1);

        log.info("트랜잭션2 시작");
        TransactionStatus transactionStatus2 = platformTransactionManager.getTransaction(new DefaultTransactionAttribute());
        log.info("트랜잭션2 롤백");
        platformTransactionManager.rollback(transactionStatus2);
    }

    /**
     * 외부 트랜잭션이 수행중인데, 내부 트랜잭션을 추가로 수행되는 상황으로 이 경우 신규 트랜잭션 isNewTransaction=true 가 된다.
     * 내부 트랜잭션을 시작하는 시점에는 이미 외부 트랜잭션이 진행중인 상태로 이 경우 내부 트랜잭션은 외부 트랜잭션에 참여한다.
     * - 내부 트랜잭션이 외부 트랜잭션에 참여한다는 뜻은 내부 트랜잭션이 외부 트랜잭션을 그대로 이어 받아서 따른다는 뜻이다.
     * - 즉, 외부 트랜잭션과 내부 트랜잭션이 하나의 물리 트랜잭션으로 묶이는 것
     * <p>
     * # 외부 트랜잭션과 내부 트랜잭션이 하나의 물리 트랜잭션으로 묶인다고 했는데, 생각해보면 트랜잭션은 하나의 커넥션에 커밋은 한번만 호출 할 수 있다.
     * 즉, 커밋 or 롤백을 하게 되면 해당 트랜잭션은 끝난다. 그렇다면 스프링은 어떻게 어떻게 외부 트랜잭션과 내부 트랜잭션을 묶어서 하나의 물리 트랜잭션으로 묶어서 동작할 수 있을까?
     */
    @Test
    void inner_commit() {
        /**
         * 내부 트랜잭션을 시작할 때 Participating in existing transaction 이라는 메시지를 확인할 수 있다. (내부 트랜잭션이 기존에 존재하는 외부 트랜잭션에 참여한다는 뜻)
         * 내부 트랜잭션이 실제 물리 트랜잭션을 커밋하면 트랜잭션이 끝나버리기 때문에, 트랜잭션을 처음 시작한 외부 트랜잭션까지 이어갈 수 없다.
         * 그렇기 때문에 내부 트랜잭션을 시작하거나 커밋할 때 DB Connection 을 통해 커밋하는 로그를 전혀 확인 할 수 없다.
         * 스프링은 여러 트랜잭션이 함께 사용되는 경우 처음 트랜잭션을 시작한 외부 트랜잭션이 실제 물리 트랜 잭션을 관리하도록 하여 트랜잭션 중복 커밋 문제를 해결한다
         */
        log.info("외부 트랜잭션 시작");
        TransactionStatus outer = platformTransactionManager.getTransaction(new DefaultTransactionAttribute());
        log.info("outer.isNewTransaction()={}", outer.isNewTransaction()); // true

        log.info("내부 트랜잭션 시작");
        TransactionStatus inner = platformTransactionManager.getTransaction(new DefaultTransactionAttribute());
        log.info("inner.isNewTransaction()={}", inner.isNewTransaction()); // false

        log.info("내부 트랜잭션 커밋");
        platformTransactionManager.commit(inner);

        log.info("외부 트랜잭션 커밋");
        platformTransactionManager.commit(outer);
    }

    @Test
    void outer_rollback() {
        log.info("외부 트랜잭션 시작");
        TransactionStatus outer = platformTransactionManager.getTransaction(new DefaultTransactionAttribute());

        log.info("내부 트랜잭션 시작");
        TransactionStatus inner = platformTransactionManager.getTransaction(new DefaultTransactionAttribute());

        log.info("내부 트랜잭션 커밋");
        platformTransactionManager.commit(inner);

        log.info("외부 트랜잭션 롤백");
        platformTransactionManager.rollback(outer);
    }

    @Test
    void inner_rollback() {
        log.info("외부 트랜잭션 시작");
        TransactionStatus outer = platformTransactionManager.getTransaction(new DefaultTransactionAttribute());
        log.info("내부 트랜잭션 시작");
        TransactionStatus inner = platformTransactionManager.getTransaction(new DefaultTransactionAttribute());

        log.info("내부 트랜잭션 롤백");
        platformTransactionManager.rollback(inner); // rollback-only 마킹

        log.info("외부 트랜잭션 커밋");
        assertThatThrownBy(() -> platformTransactionManager.commit(outer)).isInstanceOf(UnexpectedRollbackException.class);
    }

    @Test
    void inner_rollback_requires_new() {
        log.info("외부 트랜잭션 시작");
        TransactionStatus outer = platformTransactionManager.getTransaction(new DefaultTransactionAttribute());
        log.info("outer.isNewTransaction()={}", outer.isNewTransaction()); // true

        log.info("내부 트랜잭션 시작");
        DefaultTransactionAttribute defaultTransactionAttribute = new DefaultTransactionAttribute();
        /**
         * 내부 트랜잭션을 시작할 때 전파 옵션인 propagationBehavior 에 PROPAGATION_REQUIRES_NEW 옵션 부여
         * - 기존 트랜잭션을 무시하고 새로운 물리 트랜잭션을 만들어서 시작
         */
        defaultTransactionAttribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus inner = platformTransactionManager.getTransaction(defaultTransactionAttribute);
        log.info("inner.isNewTransaction()={}", inner.isNewTransaction()); // true

        log.info("내부 트랜잭션 롤백");
        platformTransactionManager.rollback(inner); // 롤백

        log.info("외부 트랜잭션 커밋");
        platformTransactionManager.commit(outer); // 커밋
    }
}