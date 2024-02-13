package hello.springtx.apply;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@SpringBootTest
public class InternalCallV1Test {
    @Autowired
    CallService callService;

    @Test
    void printProxy() {
        /**
         * 테스트에서 callService 를 주입 받는데, 해당 클래스를 출력해보면 뒤에 CGLIB...이 붙은 것을 확인할 수 있다
         * - 원본 객체 대신에 트랜잭션을 처리하는 프록시 객체를 주입 받은 것
         */
        log.info("callService class={}", callService.getClass());
    }

    @Test
    void internalCall() {
        callService.internal();
    }

    @Test
    void externalCall() {
        /**
         * 실행 로그를 보면 트랜잭션 관련 코드가 전혀 보이지 않는다. 프록시가 아닌 실제 callService 에서 남긴 로그만 확 인된다.
         * 추가로 internal() 내부에서 호출한 "tx active=false" 로그를 통해 확실히 트랜잭션이 수행되지 않은 것을 확인할 수 있다
         * 기대와 다르게 internal() 에서 트랜잭션이 전혀 적용되지 않았다. 왜 이런 문제가 발생하는 것일까?
         */
        callService.external();
    }

    @TestConfiguration
    static class InternalCallV1Config {
        @Bean
        CallService callService() {
            return new CallService();
        }
    }

    /**
     * @프록시내부호출의문제 -> internal() 에서 트랜잭션이 적용되지 않는 이유
     * - external 호출 흐름을 살펴 보자
     *   1. Client TestCode 는 callService.external() 을 호출 (여기서 callService 는 트랜잭션 프록시이다)
     *   2. callService 의 트랜잭션 프록시가 호출되지만 external() 엔 @Transaction 이 없으므로 트랜잭션 프록시는 트랜잭션을 적용하지 않는다.
     *   3. 트랜잭션을 적용하지 않고, callService 의 external() 을 호출하고 그 내부의 internal() 까지 호출한다.
     * - 과정만 보았을땐 문제가 없어보이지만 자바언어에서는 별도의 참조가 없다면 this 라는 자기 자신의 인스턴스를 가리킨다.
     * - 이때 자기 자신의 내부 메서드인 this.internal()을 호출하게 되는데, 여기서 this 는 실제 대상 객체(target)의 인스턴스를 뜻한다.
     * - 결과적으로 이러한 내부 호출은 프록시를 거치지 않고 target 에 있는 internal() 을 직접 호출하여 트랜잭션을 적용할 수 없다.
     *
     * @해결방법
     * - 문제를 해결할수 있는 방법으로 "트랜잭션을 적용해야 하는 메서드를 별도의 클래스로 분리" 가 있다.
     */
    @Slf4j
    static class CallService {
        public void external() {
            log.info("call external");
            printTxInfo();
            internal();
        }

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
