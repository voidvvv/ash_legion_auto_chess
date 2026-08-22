package com.voidvvv.kz_auto_chess_n.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 顺序发号器测试：从 1 递增、独立实例互不干扰（Phase 3 §7.1，Q1 占位实现）；
 * Phase 6 快照轨增补：peekNext 不消耗、复原构造续号。
 */
class SequentialIdIssuerTest {

    @Test
    @DisplayName("递增发号：首号 1，随后 2、3……")
    void issuesSequentialIdsFromOne() {
        SequentialIdIssuer issuer = new SequentialIdIssuer();
        assertThat(issuer.nextId()).isEqualTo(1);
        assertThat(issuer.nextId()).isEqualTo(2);
        assertThat(issuer.nextId()).isEqualTo(3);
    }

    @Test
    @DisplayName("独立实例互不干扰（各持独立计数）")
    void independentInstancesDoNotInterfere() {
        SequentialIdIssuer a = new SequentialIdIssuer();
        SequentialIdIssuer b = new SequentialIdIssuer();
        a.nextId();
        a.nextId();
        assertThat(b.nextId()).isEqualTo(1);
        assertThat(a.nextId()).isEqualTo(3);
    }

    @Test
    @DisplayName("peekNext 不消耗：连续读同值，随后 nextId 即该值")
    void peekNextDoesNotConsume() {
        SequentialIdIssuer issuer = new SequentialIdIssuer();
        issuer.nextId();
        issuer.nextId();
        assertThat(issuer.peekNext()).isEqualTo(3);
        assertThat(issuer.peekNext()).isEqualTo(3);
        assertThat(issuer.nextId()).isEqualTo(3);
        assertThat(issuer.peekNext()).isEqualTo(4);
    }

    @Test
    @DisplayName("复原构造：从指定下一号续发（单一 id 空间跨挂起不断档）")
    void restoreConstructorResumes() {
        SequentialIdIssuer issuer = new SequentialIdIssuer(41);
        assertThat(issuer.peekNext()).isEqualTo(41);
        assertThat(issuer.nextId()).isEqualTo(41);
        assertThat(issuer.nextId()).isEqualTo(42);
    }

    @Test
    @DisplayName("复原构造越界（0）即死")
    void restoreConstructorRejectsNonPositive() {
        assertThatThrownBy(() -> new SequentialIdIssuer(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("≥ 1");
    }
}
