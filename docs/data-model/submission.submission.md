# submission.submission

## Columns

| Name               | Type                     | Default | Nullable | Children                                                                                                                | Parents | Comment |
| ------------------ | ------------------------ | ------- | -------- | ----------------------------------------------------------------------------------------------------------------------- | ------- | ------- |
| broker_ref         | varchar(128)             |         | false    |                                                                                                                         |         |         |
| id                 | uuid                     |         | false    | [submission.exposure_item](submission.exposure_item.md) [submission.submission_triage](submission.submission_triage.md) |         |         |
| insured_name       | varchar(256)             |         | false    |                                                                                                                         |         |         |
| product_line       | varchar(64)              |         | false    |                                                                                                                         |         |         |
| received_at        | timestamp with time zone | now()   | false    |                                                                                                                         |         |         |
| routed_target      | varchar(64)              |         | true     |                                                                                                                         |         |         |
| screening_alert_id | uuid                     |         | true     |                                                                                                                         |         |         |
| screening_hold     | boolean                  | false   | false    |                                                                                                                         |         |         |
| status             | varchar(16)              |         | false    |                                                                                                                         |         |         |
| updated_at         | timestamp with time zone | now()   | false    |                                                                                                                         |         |         |
| uw_submission_ref  | varchar(64)              |         | true     |                                                                                                                         |         |         |

## Constraints

| Name                    | Type        | Definition                                                                                                                                                                |
| ----------------------- | ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| submission_pkey         | PRIMARY KEY | PRIMARY KEY (id)                                                                                                                                                          |
| submission_status_check | CHECK       | CHECK (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'TRIAGED'::character varying, 'ROUTED'::character varying, 'DECLINED'::character varying])::text[]))) |

## Indexes

| Name            | Definition                                                                    |
| --------------- | ----------------------------------------------------------------------------- |
| submission_pkey | CREATE UNIQUE INDEX submission_pkey ON submission.submission USING btree (id) |

## Relations

```mermaid
erDiagram

"submission.exposure_item" }o--|| "submission.submission" : "FOREIGN KEY (submission_id) REFERENCES submission.submission(id)"
"submission.submission_triage" }o--|| "submission.submission" : "FOREIGN KEY (submission_id) REFERENCES submission.submission(id)"

"submission.submission" {
  varchar_128_ broker_ref
  uuid id
  varchar_256_ insured_name
  varchar_64_ product_line
  timestamp_with_time_zone received_at
  varchar_64_ routed_target
  uuid screening_alert_id
  boolean screening_hold
  varchar_16_ status
  timestamp_with_time_zone updated_at
  varchar_64_ uw_submission_ref
}
"submission.exposure_item" {
  boolean cat_flag
  varchar_64_ country
  timestamp_with_time_zone created_at
  varchar_8_ currency
  uuid id
  text loss_history
  varchar_128_ occupancy
  varchar_16_ postcode
  varchar_256_ site_name
  uuid submission_id FK
  numeric_18_2_ tiv_amount
}
"submission.submission_triage" {
  varchar_128_ actor
  varchar_32_ appetite_flag
  integer completeness_score
  timestamp_with_time_zone decided_at
  varchar_16_ decision
  bigint id
  text reason
  uuid submission_id FK
}
```

---

> Generated by [tbls](https://github.com/k1LoW/tbls)
