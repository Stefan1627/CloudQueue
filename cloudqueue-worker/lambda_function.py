import json
from datetime import datetime, timezone

import boto3
from botocore.exceptions import ClientError

dynamodb = boto3.client("dynamodb")

TABLE_NAME = "CloudQueueJobs"


def now_iso():
    return datetime.now(timezone.utc).isoformat()


def lambda_handler(event, context):
    for record in event["Records"]:
        body = json.loads(record["body"])

        job_id = body["jobId"]
        job_type = body["type"]

        payload = body["payload"]
        # Defensive parsing: allow payload to be dict or JSON string
        if isinstance(payload, str):
            payload = json.loads(payload)

        # 1️⃣ Claim the job (idempotent)
        try:
            dynamodb.update_item(
                TableName=TABLE_NAME,
                Key={"jobId": {"S": job_id}},
                UpdateExpression="SET #s = :in_progress, updatedAt = :now",
                ConditionExpression="#s = :queued",
                ExpressionAttributeNames={
                    "#s": "status"
                },
                ExpressionAttributeValues={
                    ":queued": {"S": "QUEUED"},
                    ":in_progress": {"S": "IN_PROGRESS"},
                    ":now": {"S": now_iso()},
                },
            )
        except ClientError as e:
            if e.response["Error"]["Code"] == "ConditionalCheckFailedException":
                # Job already claimed or finished → do not acknowledge SQS message
                print(f"Job {job_id} already claimed or processed")
                return
            raise

        # 2️⃣ Process the job
        try:
            result = process_job(job_type, payload)

            # 3️⃣ Mark job as SUCCEEDED
            dynamodb.update_item(
                TableName=TABLE_NAME,
                Key={"jobId": {"S": job_id}},
                UpdateExpression="SET #s = :success, #r = :result, updatedAt = :now",
                ExpressionAttributeNames={
                    "#s": "status",
                    "#r": "result"
                },
                ExpressionAttributeValues={
                    ":success": {"S": "SUCCEEDED"},
                    ":result": {"S": json.dumps(result)},
                    ":now": {"S": now_iso()},
                },
            )

        except Exception as e:
            # 4️⃣ Mark job as FAILED (reserved keyword safe)
            dynamodb.update_item(
                TableName=TABLE_NAME,
                Key={"jobId": {"S": job_id}},
                UpdateExpression="SET #s = :failed, #err = :error, updatedAt = :now",
                ExpressionAttributeNames={
                    "#s": "status",
                    "#err": "error"
                },
                ExpressionAttributeValues={
                    ":failed": {"S": "FAILED"},
                    ":error": {"S": str(e)},
                    ":now": {"S": now_iso()},
                },
            )
            # Re-raise to trigger SQS retry / DLQ
            raise


def process_job(job_type, payload):
    if job_type == "transformText":
        text = payload["text"]
        return {
            "originalLength": len(text),
            "upper": text.upper(),
            "wordCount": len(text.split()),
        }

    raise ValueError(f"Unsupported job type: {job_type}")
