CREATE TABLE IF NOT EXISTS issue_board_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    row_order INT NULL,
    problem VARCHAR(500) NULL,
    priority VARCHAR(120) NULL,
    owner_name VARCHAR(255) NULL,
    issue_date VARCHAR(40) NULL,
    root_cause VARCHAR(500) NULL,
    actions VARCHAR(1500) NULL,
    responsible VARCHAR(255) NULL,
    target_date DATE NULL,
    target_date_remark VARCHAR(500) NULL,
    target_date_extension1 DATE NULL,
    target_date_extension1_remark VARCHAR(500) NULL,
    target_date_extension2 DATE NULL,
    target_date_extension2_remark VARCHAR(500) NULL,
    due_days INT NULL,
    status VARCHAR(80) NULL,
    completed_date DATE NULL,
    remarks VARCHAR(500) NULL,
    last_review_date DATE NULL,
    next_review_date DATE NULL,
    board_date DATE NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_issue_board_items_board_date (board_date),
    INDEX idx_issue_board_items_updated_at (updated_at)
);

CREATE TABLE IF NOT EXISTS issue_board_item_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issue_board_item_id BIGINT NULL,
    field_name VARCHAR(120) NULL,
    old_value VARCHAR(255) NULL,
    new_value VARCHAR(255) NULL,
    edited_by VARCHAR(255) NULL,
    edited_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_issue_board_item_history_item (issue_board_item_id)
);

UPDATE abnormality_tracker SET row_order = NULL WHERE row_order IS NOT NULL AND (TRIM(row_order) = '' OR TRIM(row_order) NOT REGEXP '^-?[0-9]+$');
UPDATE abnormality_tracker SET yellow_tags = NULL WHERE yellow_tags IS NOT NULL AND (TRIM(yellow_tags) = '' OR TRIM(yellow_tags) NOT REGEXP '^-?[0-9]+$');
UPDATE abnormality_tracker SET red_tags = NULL WHERE red_tags IS NOT NULL AND (TRIM(red_tags) = '' OR TRIM(red_tags) NOT REGEXP '^-?[0-9]+$');
UPDATE abnormality_tracker SET closure_percent = NULL WHERE closure_percent IS NOT NULL AND (TRIM(closure_percent) = '' OR TRIM(closure_percent) NOT REGEXP '^-?[0-9]+([.][0-9]+)?$');

UPDATE app_license SET user_count = 1 WHERE user_count IS NOT NULL AND (TRIM(user_count) = '' OR TRIM(user_count) NOT REGEXP '^-?[0-9]+$');

UPDATE daily_performance SET month_target = 0 WHERE month_target IS NOT NULL AND (TRIM(month_target) = '' OR TRIM(month_target) NOT REGEXP '^-?[0-9]+([.][0-9]+)?$');
UPDATE daily_performance SET actual_mtd = 0 WHERE actual_mtd IS NOT NULL AND (TRIM(actual_mtd) = '' OR TRIM(actual_mtd) NOT REGEXP '^-?[0-9]+([.][0-9]+)?$');
UPDATE daily_performance SET daily_target = 0 WHERE daily_target IS NOT NULL AND (TRIM(daily_target) = '' OR TRIM(daily_target) NOT REGEXP '^-?[0-9]+([.][0-9]+)?$');
UPDATE daily_performance SET yesterday = 0 WHERE yesterday IS NOT NULL AND (TRIM(yesterday) = '' OR TRIM(yesterday) NOT REGEXP '^-?[0-9]+([.][0-9]+)?$');

UPDATE email_config SET port = 587 WHERE port IS NOT NULL AND (TRIM(port) = '' OR TRIM(port) NOT REGEXP '^[0-9]+$');

UPDATE gemba_schedule_items SET row_order = NULL WHERE row_order IS NOT NULL AND (TRIM(row_order) = '' OR TRIM(row_order) NOT REGEXP '^-?[0-9]+$');

UPDATE hs_daily_cross SET year = 1970 WHERE year IS NOT NULL AND (TRIM(year) = '' OR TRIM(year) NOT REGEXP '^[0-9]+$');
UPDATE hs_daily_cross SET month = 1 WHERE month IS NOT NULL AND (TRIM(month) = '' OR TRIM(month) NOT REGEXP '^[0-9]+$');
UPDATE hs_daily_cross SET day = 1 WHERE day IS NOT NULL AND (TRIM(day) = '' OR TRIM(day) NOT REGEXP '^[0-9]+$');

UPDATE issue_board_items SET row_order = NULL WHERE row_order IS NOT NULL AND (TRIM(row_order) = '' OR TRIM(row_order) NOT REGEXP '^-?[0-9]+$');
UPDATE issue_board_items SET due_days = NULL WHERE due_days IS NOT NULL AND (TRIM(due_days) = '' OR TRIM(due_days) NOT REGEXP '^-?[0-9]+$');

UPDATE leadership_gemba_tracker SET target_ytd = NULL WHERE target_ytd IS NOT NULL AND (TRIM(target_ytd) = '' OR TRIM(target_ytd) NOT REGEXP '^-?[0-9]+$');
UPDATE leadership_gemba_tracker SET target_mtd = NULL WHERE target_mtd IS NOT NULL AND (TRIM(target_mtd) = '' OR TRIM(target_mtd) NOT REGEXP '^-?[0-9]+$');
UPDATE leadership_gemba_tracker SET week1target = NULL WHERE week1target IS NOT NULL AND (TRIM(week1target) = '' OR TRIM(week1target) NOT REGEXP '^-?[0-9]+$');
UPDATE leadership_gemba_tracker SET week1actual = NULL WHERE week1actual IS NOT NULL AND (TRIM(week1actual) = '' OR TRIM(week1actual) NOT REGEXP '^-?[0-9]+$');
UPDATE leadership_gemba_tracker SET week2target = NULL WHERE week2target IS NOT NULL AND (TRIM(week2target) = '' OR TRIM(week2target) NOT REGEXP '^-?[0-9]+$');
UPDATE leadership_gemba_tracker SET week2actual = NULL WHERE week2actual IS NOT NULL AND (TRIM(week2actual) = '' OR TRIM(week2actual) NOT REGEXP '^-?[0-9]+$');
UPDATE leadership_gemba_tracker SET week3target = NULL WHERE week3target IS NOT NULL AND (TRIM(week3target) = '' OR TRIM(week3target) NOT REGEXP '^-?[0-9]+$');
UPDATE leadership_gemba_tracker SET week3actual = NULL WHERE week3actual IS NOT NULL AND (TRIM(week3actual) = '' OR TRIM(week3actual) NOT REGEXP '^-?[0-9]+$');
UPDATE leadership_gemba_tracker SET week4target = NULL WHERE week4target IS NOT NULL AND (TRIM(week4target) = '' OR TRIM(week4target) NOT REGEXP '^-?[0-9]+$');
UPDATE leadership_gemba_tracker SET week4actual = NULL WHERE week4actual IS NOT NULL AND (TRIM(week4actual) = '' OR TRIM(week4actual) NOT REGEXP '^-?[0-9]+$');
UPDATE leadership_gemba_tracker SET compliance_percent = NULL WHERE compliance_percent IS NOT NULL AND (TRIM(compliance_percent) = '' OR TRIM(compliance_percent) NOT REGEXP '^-?[0-9]+([.][0-9]+)?$');
UPDATE leadership_gemba_tracker SET week1closed = 1 WHERE UPPER(TRIM(week1closed)) IN ('TRUE', 'YES', 'Y');
UPDATE leadership_gemba_tracker SET week1closed = 0 WHERE week1closed IS NOT NULL AND TRIM(week1closed) <> '' AND UPPER(TRIM(week1closed)) NOT IN ('1', 'TRUE', 'YES', 'Y');
UPDATE leadership_gemba_tracker SET week2closed = 1 WHERE UPPER(TRIM(week2closed)) IN ('TRUE', 'YES', 'Y');
UPDATE leadership_gemba_tracker SET week2closed = 0 WHERE week2closed IS NOT NULL AND TRIM(week2closed) <> '' AND UPPER(TRIM(week2closed)) NOT IN ('1', 'TRUE', 'YES', 'Y');
UPDATE leadership_gemba_tracker SET week3closed = 1 WHERE UPPER(TRIM(week3closed)) IN ('TRUE', 'YES', 'Y');
UPDATE leadership_gemba_tracker SET week3closed = 0 WHERE week3closed IS NOT NULL AND TRIM(week3closed) <> '' AND UPPER(TRIM(week3closed)) NOT IN ('1', 'TRUE', 'YES', 'Y');
UPDATE leadership_gemba_tracker SET week4closed = 1 WHERE UPPER(TRIM(week4closed)) IN ('TRUE', 'YES', 'Y');
UPDATE leadership_gemba_tracker SET week4closed = 0 WHERE week4closed IS NOT NULL AND TRIM(week4closed) <> '' AND UPPER(TRIM(week4closed)) NOT IN ('1', 'TRUE', 'YES', 'Y');

UPDATE lsr_daily_tracking SET year = 1970 WHERE year IS NOT NULL AND (TRIM(year) = '' OR TRIM(year) NOT REGEXP '^[0-9]+$');
UPDATE lsr_daily_tracking SET month = 1 WHERE month IS NOT NULL AND (TRIM(month) = '' OR TRIM(month) NOT REGEXP '^[0-9]+$');
UPDATE lsr_daily_tracking SET day = 1 WHERE day IS NOT NULL AND (TRIM(day) = '' OR TRIM(day) NOT REGEXP '^[0-9]+$');

UPDATE process_confirmation_configs SET jan_score = NULL WHERE jan_score IS NOT NULL AND (TRIM(jan_score) = '' OR TRIM(jan_score) NOT REGEXP '^-?[0-9]+$');
UPDATE process_confirmation_configs SET feb_score = NULL WHERE feb_score IS NOT NULL AND (TRIM(feb_score) = '' OR TRIM(feb_score) NOT REGEXP '^-?[0-9]+$');
UPDATE process_confirmation_configs SET mar_score = NULL WHERE mar_score IS NOT NULL AND (TRIM(mar_score) = '' OR TRIM(mar_score) NOT REGEXP '^-?[0-9]+$');
UPDATE process_confirmation_configs SET apr_score = NULL WHERE apr_score IS NOT NULL AND (TRIM(apr_score) = '' OR TRIM(apr_score) NOT REGEXP '^-?[0-9]+$');
UPDATE process_confirmation_configs SET may_score = NULL WHERE may_score IS NOT NULL AND (TRIM(may_score) = '' OR TRIM(may_score) NOT REGEXP '^-?[0-9]+$');
UPDATE process_confirmation_configs SET jun_score = NULL WHERE jun_score IS NOT NULL AND (TRIM(jun_score) = '' OR TRIM(jun_score) NOT REGEXP '^-?[0-9]+$');
UPDATE process_confirmation_configs SET jul_score = NULL WHERE jul_score IS NOT NULL AND (TRIM(jul_score) = '' OR TRIM(jul_score) NOT REGEXP '^-?[0-9]+$');
UPDATE process_confirmation_configs SET aug_score = NULL WHERE aug_score IS NOT NULL AND (TRIM(aug_score) = '' OR TRIM(aug_score) NOT REGEXP '^-?[0-9]+$');
UPDATE process_confirmation_configs SET sep_score = NULL WHERE sep_score IS NOT NULL AND (TRIM(sep_score) = '' OR TRIM(sep_score) NOT REGEXP '^-?[0-9]+$');
UPDATE process_confirmation_configs SET oct_score = NULL WHERE oct_score IS NOT NULL AND (TRIM(oct_score) = '' OR TRIM(oct_score) NOT REGEXP '^-?[0-9]+$');
UPDATE process_confirmation_configs SET nov_score = NULL WHERE nov_score IS NOT NULL AND (TRIM(nov_score) = '' OR TRIM(nov_score) NOT REGEXP '^-?[0-9]+$');
UPDATE process_confirmation_configs SET dec_score = NULL WHERE dec_score IS NOT NULL AND (TRIM(dec_score) = '' OR TRIM(dec_score) NOT REGEXP '^-?[0-9]+$');
UPDATE process_confirmation_configs SET ytd_score = NULL WHERE ytd_score IS NOT NULL AND (TRIM(ytd_score) = '' OR TRIM(ytd_score) NOT REGEXP '^-?[0-9]+$');

UPDATE production_metric_custom_definitions SET decimal_places = NULL WHERE decimal_places IS NOT NULL AND (TRIM(decimal_places) = '' OR TRIM(decimal_places) NOT REGEXP '^-?[0-9]+$');
UPDATE production_metric_custom_definitions SET display_order = NULL WHERE display_order IS NOT NULL AND (TRIM(display_order) = '' OR TRIM(display_order) NOT REGEXP '^-?[0-9]+$');
UPDATE production_metric_custom_definitions SET active_flag = 1 WHERE active_flag IS NOT NULL AND UPPER(TRIM(active_flag)) IN ('TRUE', 'YES', 'Y');
UPDATE production_metric_custom_definitions SET active_flag = 0 WHERE active_flag IS NOT NULL AND TRIM(active_flag) <> '' AND UPPER(TRIM(active_flag)) NOT IN ('1', 'TRUE', 'YES', 'Y');
ALTER TABLE production_metric_custom_definitions ADD COLUMN graph_visible BIT NOT NULL DEFAULT 1;
ALTER TABLE production_metric_custom_definitions ADD COLUMN table_visible BIT NOT NULL DEFAULT 1;
UPDATE production_metric_custom_definitions SET graph_visible = 1 WHERE graph_visible IS NULL;
UPDATE production_metric_custom_definitions SET table_visible = 1 WHERE table_visible IS NULL;

UPDATE training_schedule_items SET row_order = NULL WHERE row_order IS NOT NULL AND (TRIM(row_order) = '' OR TRIM(row_order) NOT REGEXP '^-?[0-9]+$');
UPDATE training_schedule_items SET target_percent = NULL WHERE target_percent IS NOT NULL AND (TRIM(target_percent) = '' OR TRIM(target_percent) NOT REGEXP '^-?[0-9]+$');
UPDATE training_schedule_items SET duration_hours = NULL WHERE duration_hours IS NOT NULL AND (TRIM(duration_hours) = '' OR TRIM(duration_hours) NOT REGEXP '^-?[0-9]+([.][0-9]+)?$');

ALTER TABLE app_users MODIFY COLUMN page_view_permissions TEXT NULL;
ALTER TABLE app_users MODIFY COLUMN page_edit_permissions TEXT NULL;
ALTER TABLE production_metric_custom_values MODIFY COLUMN `ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metric_custom_values MODIFY COLUMN `ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metric_custom_values MODIFY COLUMN `mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metric_custom_values MODIFY COLUMN `mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metric_custom_values MODIFY COLUMN `ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metric_custom_values MODIFY COLUMN `ytd_target` VARCHAR(64) NULL;

ALTER TABLE production_metrics MODIFY COLUMN `production_productivity_ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `production_productivity_ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `production_productivity_mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `production_productivity_mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `production_productivity_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `production_productivity_ytd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `logistics_productivity_ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `logistics_productivity_ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `logistics_productivity_mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `logistics_productivity_mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `logistics_productivity_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `logistics_productivity_ytd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_sensory_score_ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_sensory_score_ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_sensory_score_mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_sensory_score_mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_sensory_score_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_sensory_score_ytd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__consumer_complaint_units_/_mhl_ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__consumer_complaint__units_/_mhl_ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_consumer_complaint_units_mhl_mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_consumer_complaint_units_mhl_mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_consumer_complaint_units_mhl_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_consumer_complaint_units_mhl_ytd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__customer_complaint__units_/_mhl_ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__customer_complaint__units_/_mhl_ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_customer_complaint_units_mhl_mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_customer_complaint_units_mhl_mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_customer_complaint_units_mhl_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_customer_complaint_units_mhl_ytd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `process_confirmation_b&p_ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `process_confirmation_b&p_ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `process_confirmation_bp_mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `process_confirmation_bp_mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `process_confirmation_bp_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `process_confirmation_bp_ytd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `process_confirmation_pack_ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `process_confirmation_pack_ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `process_confirmation_pack_mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `process_confirmation_pack_mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `process_confirmation_pack_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `process_confirmation_pack_ytd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__oee__ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__oee__ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__oee__mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__oee__mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_oee_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_oee_ytd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__beer_loss__ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__beer_loss__ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__beer_loss__mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__beer_loss__mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_beer_loss_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_beer_loss_ytd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__wur_hl/hl_ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__wur_hl/hl_ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__wur_hl/hl_mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__wur_hl/hl_mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_wur_hlhl_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_wur_hlhl_ytd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__electricity_kwh/hl_ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__electricity_kwh/hl_ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__electricity_kwh/hl_mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__electricity_kwh/hl_mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_electricity_kwh_hl_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_electricity_kwh_hl_ytd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__energy_kwh/hl_ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__energy_kwh/hl_ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__energy_kwh/hl_mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__energy_kwh/hl_mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_energy_kwh_hl_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_energy_kwh_hl_ytd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `no_of_brews_ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `no_of_brews_ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `no_of_brews_mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `no_of_brews_mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `no_of_brews_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `no_of_brews_ytd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `dispatch_ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `dispatch_ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `dispatch_mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `dispatch_mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `dispatch_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `dispatch_ytd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__rgb_ratio__ftd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__rgb_ratio__ftd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__rgb_ratio__mtd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi__rgb_ratio__mtd_target` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_rgb_ratio_ytd_actual` VARCHAR(64) NULL;
ALTER TABLE production_metrics MODIFY COLUMN `kpi_rgb_ratio_ytd_target` VARCHAR(64) NULL;
