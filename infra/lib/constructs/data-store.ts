import { Construct } from 'constructs';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { RemovalPolicy } from 'aws-cdk-lib';
import { DYNAMODB } from '../configuration/constants';

export interface DataStoreConstructProps {
  readonly stage: string;
}

export class DataStoreConstruct extends Construct {
  public readonly flightPriceHistoryTable: dynamodb.Table;

  constructor(scope: Construct, id: string, props: DataStoreConstructProps) {
    super(scope, id);

    this.flightPriceHistoryTable = new dynamodb.Table(this, 'FlightPriceHistory', {
      tableName: DYNAMODB.tableName(props.stage),
      partitionKey: { name: DYNAMODB.partitionKey, type: dynamodb.AttributeType.STRING },
      sortKey: { name: DYNAMODB.sortKey, type: dynamodb.AttributeType.STRING },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: RemovalPolicy.DESTROY,
    });
  }
}
