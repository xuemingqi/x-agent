-- Identity data needed by AI ownership, login and public-resource administration.
-- No sample accounts or passwords are included.
CREATE TABLE IF NOT EXISTS `user`
(
    id          bigint                              NOT NULL PRIMARY KEY,
    name        varchar(100)                        NOT NULL,
    sex         tinyint                             DEFAULT NULL,
    mobile      varchar(32)                         NOT NULL,
    is_admin    tinyint(1)                          NOT NULL DEFAULT 0,
    password    varchar(255)                        NOT NULL,
    salt        varchar(128)                        NOT NULL,
    create_time datetime                            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime                            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_mobile (mobile)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
