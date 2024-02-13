package hello.springtx.apply;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
public class InternalCallV2Test {

    @Autowired
    CallService callService;

    @Test
    void externalCallV2() {
        callService.external();
    }

    @TestConfiguration
    static class InternalCallV2Config {
        @Bean
        CallService callService() {
            return new CallService(innerService());
        }

        @Bean
        InternalService innerService() {
            return new InternalService();
        }
    }

    @Slf4j
    @RequiredArgsConstructor
    static class CallService {
        /**
         * - callService 는 주입 받은 internalService.internal() 을 호출
         * - internalService 는 트랜잭션 프록시로 internal() 메서드에 @Transactional 이 붙어 있으 므로 트랜잭션 프록시는 트랜잭션을 적용한다.
         * - 트랜잭션 적용 후 실제 internalService 객체 인스턴스의 internal() 을 호출한다
         */
        private final InternalService internalService;

        public void external() {
            log.info("call external");
            printTxInfo();
            internalService.internal();
        }

        private void printTxInfo() {
            boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();
            log.info("tx active={}", txActive);
        }
    }

    @Slf4j
    static class InternalService {
        /**
         * @Transaction AOP 기술은 public method 에만 트랜잭션을 적용하도록 기본 설정이 되어 있다.
         * - 아래처럼 클래스 레벨에 트랜잭션을 적용한경우 모든 메서드에 트랜잭션이 걸릴수 있다. (이렇게 된다면 의도하지 않은 비즈니스 로직까지 트랜잭션이 과도하게 걸리게 된다)
         * - 트랜잭션은 주로 비즈니스 로직의 시작점에 걸기 때문에 대부분 외부에 열어준 곳을 시작점으로 사용한다.
         * - 이러한 이우로 public 메서드에만 트랜잭션을 적용하도록 설정되어 있다.
         *
         * @Transaction
         * public class Hello {
         *   public method1();
         *   method2():
         *   protected method3();
         *   private method4();
         * }
         */
        @Transactional
        public void internal() {
            log.info("call internal");
            printTxInfo();
        }

        private void printTxInfo() {
            boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();
            log.info("tx active={}", txActive);
        }
    }
}