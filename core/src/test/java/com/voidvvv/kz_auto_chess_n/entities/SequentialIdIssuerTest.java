package com.voidvvv.kz_auto_chess_n.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 顺序发号器测试：从 1 递增、独立实例互不干扰（Phase 3 §7.1，Q1 占位实现）。
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
}
