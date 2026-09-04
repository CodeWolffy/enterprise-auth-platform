package com.enterprise.auth.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.auth.platform.modules.notification.infrastructure.mapper.SysUserNotificationMapper;
import java.time.Instant;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class NotificationInboxMapperSqlTest {

    @Test
    void countQueryShouldUseTheSameUnionSourcesAndReadFilterAsInbox() throws NoSuchMethodException {
        Select select = SysUserNotificationMapper.class
                .getMethod("countVisibleNotifications", String.class, Long.class, Boolean.class, Instant.class)
                .getAnnotation(Select.class);

        String sql = String.join(" ", select.value());
        assertThat(sql)
                .contains("SELECT COUNT(*)")
                .contains("sys_user_notification")
                .contains("sys_notice_read_status")
                .contains("UNION ALL")
                .contains("inbox.read_at IS NOT NULL")
                .contains("inbox.read_at IS NULL");
    }

    @Test
    void listQueryShouldPageTheUnifiedProjectionInDatabaseWithStableOrdering() throws NoSuchMethodException {
        Select select = SysUserNotificationMapper.class
                .getMethod(
                        "listVisibleNotifications",
                        String.class,
                        Long.class,
                        Boolean.class,
                        Instant.class,
                        int.class,
                        int.class)
                .getAnnotation(Select.class);

        String sql = String.join(" ", select.value());
        assertThat(sql)
                .contains("UNION ALL")
                .contains("0 AS broadcast_flag")
                .contains("1 AS broadcast_flag")
                .contains("ORDER BY (inbox.read_at IS NULL) DESC")
                .contains("inbox.created_at DESC")
                .contains("ABS(inbox.id) DESC")
                .contains("LIMIT #{offset}, #{limit}");
    }
}
