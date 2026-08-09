CREATE TABLE favorites (
    id binary(16) NOT NULL,
    user_id binary(16) NOT NULL,
    content_id varchar(50) NOT NULL,
    title varchar(200) NOT NULL,
    address varchar(500) DEFAULT NULL,
    first_image varchar(500) DEFAULT NULL,
    category enum('ATTRACTION','CULTURE','EXPERIENCE','FESTIVAL','FOOD','NATURE') DEFAULT NULL,
    summary text,
    indoor bit(1) DEFAULT NULL,
    region enum('HADONG','YECHEON','YEONGJU') NOT NULL,
    created_at datetime(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorites_user_content (user_id, content_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
