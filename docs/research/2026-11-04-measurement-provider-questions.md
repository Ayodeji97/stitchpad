# Questions for measurement engine evaluation

Prepared for future enquiries; no messages sent and no vendor selected.

## Accuracy and fit

1. What exactly is each measurement definition, anatomical level, path, pose and unit?
2. Which fields are measured/predicted from photos versus inferred from supplied stats?
3. Can you provide held-out field-level bias, MAE, P95 absolute error, agreement bounds
   and complete essential-set acceptance, including failures and missing fields?
4. Which phones, body-size ranges, skin tones, clothing conditions and customer
   populations were validated? Was home capture tested separately from supervised capture?
5. How accurate must the supplied height be, and how does its error propagate?
6. Which fields are unsuitable for bespoke cutting? What manual checks do you require?
7. Are uncertainty values calibrated error bounds or just capture-quality/pose scores?
8. Do model/SDK upgrades change definitions or results? Can we pin a version and rollback?
9. Is there evidence of actual garment fit and alteration/remake rates?

## Integration

10. Is a browser link supported on our actual low-end devices and WhatsApp in-app browsers?
11. What is the complete OS/browser/camera/sensor compatibility matrix?
12. Can capture work without a helper? Are English and French text/voice instructions available?
13. Which capture steps and quality validations can we customise without reducing validity?
14. Does offline mean capture only, or full inference? What is retained on the device?
15. How are callbacks authenticated, scoped and retried? Can we use idempotency keys,
    retrieve job results securely, cancel jobs and receive deletion acknowledgements?
16. What are observed load/processing latency, payload sizes, quotas, rate limits and outage behaviour?

## Commercial terms

17. What is the exact API/SDK/white-label price, minimum spend, setup fee and trial limit?
18. Are failed scans, quality rejections, retries and retrieval calls billed?
19. Can we export measurements and provenance and leave without losing customer records?
20. What code, model, asset and training-data rights cover our intended commercial usage?

## Customer data

21. Which images, inferred meshes and measurements leave the phone, and to which regions/subprocessors?
22. What are the exact raw-media, output, log and backup retention/deletion periods?
23. Is any input/output used to train or improve models? Can this be contractually disabled?
24. Can customers decline photo retention/sharing while still using manual measurement?
25. What DPA, incident response, access controls and audit evidence are available?
26. Can raw-media deletion and downstream erasure be verified per job, without a support email?

## Our own engine must answer the same questions

Self-hosting does not remove requirements for representative data, licence rights,
measurement definitions, customer consent, reference validation, versioning or incident
handling. Add measured compute/memory/latency and cost-per-accepted-set evidence.
