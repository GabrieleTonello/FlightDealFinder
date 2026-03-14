import * as cdk from 'aws-cdk-lib';
import * as pipelines from 'aws-cdk-lib/pipelines';
import { Construct } from 'constructs';
import { STAGE_CONFIGS } from './stageConfig';
import { createFlightDealStacks, DeploymentEnvironment } from '../stacks';

export interface FlightDealPipelineProps extends cdk.StackProps {
  githubOwner: string;
  githubRepo: string;
  githubBranch?: string;
}

export class FlightDealPipeline extends cdk.Stack {
  constructor(scope: Construct, id: string, props: FlightDealPipelineProps) {
    super(scope, id, props);

    const source = pipelines.CodePipelineSource.gitHub(
      `${props.githubOwner}/${props.githubRepo}`,
      props.githubBranch || 'main',
    );

    const synthStep = new pipelines.ShellStep('Synth', {
      input: source,
      installCommands: [
        'yum install -y java-21-amazon-corretto-devel',
        'alternatives --set java /usr/lib/jvm/java-21-amazon-corretto/bin/java',
        'alternatives --set javac /usr/lib/jvm/java-21-amazon-corretto/bin/javac',
        'export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto',
      ],
      commands: [
        'export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto',
        'export PATH=$JAVA_HOME/bin:$PATH',
        'java -version',
        'cd service && ./gradlew clean build && cd ..',
        'cd infra && npm ci && npm run build && npx cdk synth',
      ],
      primaryOutputDirectory: 'infra/cdk.out',
    });

    const pipeline = new pipelines.CodePipeline(this, 'Pipeline', {
      pipelineName: 'FlightDealNotifier-Pipeline',
      synth: synthStep,
      crossAccountKeys: true,
    });

    STAGE_CONFIGS.forEach((config) => {
      const stage = new cdk.Stage(this, `${config.name}DeploymentGroup`, {
        env: config.env,
        stageName: `${config.name.toLowerCase()}-deployment-group`,
      });

      const env: DeploymentEnvironment = {
        ...config.env,
        stageName: config.name.toLowerCase(),
      };

      createFlightDealStacks(stage, env, config);

      const isDevStage = config.name === 'Dev';
      const isProdStage = config.name === 'Prod';

      pipeline.addStage(stage, {
        // After Dev: run integration tests
        ...(isDevStage && {
          post: [
            new pipelines.ShellStep('IntegrationTests', {
              input: source,
              commands: [
                'cd integration-tests',
                './gradlew test',
              ],
            }),
          ],
        }),
        // Before Prod: require manual approval
        ...(isProdStage && {
          pre: [
            new pipelines.ManualApprovalStep('PromoteToProd', {
              comment: 'Dev integration tests passed. Approve deployment to production?',
            }),
          ],
        }),
      });
    });
  }
}
