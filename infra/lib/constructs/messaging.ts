import { Construct } from 'constructs';
import { Duration } from 'aws-cdk-lib';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as subscriptions from 'aws-cdk-lib/aws-sns-subscriptions';
import { MESSAGING } from '../configuration/constants';

export interface MessagingConstructProps {
  readonly stage: string;
}

export class MessagingConstruct extends Construct {
  public readonly dealTopic: sns.Topic;
  public readonly dealQueue: sqs.Queue;
  public readonly deadLetterQueue: sqs.Queue;

  constructor(scope: Construct, id: string, props: MessagingConstructProps) {
    super(scope, id);

    this.deadLetterQueue = new sqs.Queue(this, 'DeadLetterQueue', {
      queueName: MESSAGING.dlqName(props.stage),
      retentionPeriod: Duration.days(MESSAGING.dlqRetentionDays),
    });

    this.dealQueue = new sqs.Queue(this, 'DealQueue', {
      queueName: MESSAGING.queueName(props.stage),
      visibilityTimeout: Duration.seconds(MESSAGING.visibilityTimeoutSeconds),
      deadLetterQueue: {
        queue: this.deadLetterQueue,
        maxReceiveCount: MESSAGING.maxReceiveCount,
      },
    });

    this.dealTopic = new sns.Topic(this, 'DealTopic', {
      topicName: MESSAGING.topicName(props.stage),
    });

    this.dealTopic.addSubscription(new subscriptions.SqsSubscription(this.dealQueue));
  }
}
