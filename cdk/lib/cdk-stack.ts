import ec2 = require('@aws-cdk/aws-ec2');
import ecs = require('@aws-cdk/aws-ecs');
import cdk = require('@aws-cdk/core');
import * as apigateway from '@aws-cdk/aws-apigateway';
import * as lambda from '@aws-cdk/aws-lambda';


export class SupportBotVersionAnnounceStack extends cdk.Stack {
  constructor(scope: cdk.Construct, id: string, props?: cdk.StackProps)  {

    super(scope, id, props);

    const api = new apigateway.RestApi(this, "support-bot-announce", {
      restApiName: "Support Bot Webhooks",
      description: "Handle new version releases and jira ticket fixes."
    });

    const handler = new lambda.Function(this, "SupportBotVersionAnnounceFn", {
      runtime: lambda.Runtime.PROVIDED,
      code: lambda.Code.fromAsset('../.holy-lambda/build/latest.zip'),
      handler: "org.singularity-group.bot-announce.core.BotAnnounceLambda",
    });

    const BotAnnounce = new apigateway.LambdaIntegration(handler);
    api.root.addMethod("POST");
    const announceResource = api.root.addResource("slack-announce", {
      defaultIntegration: BotAnnounce
    })
    announceResource.addMethod("POST", BotAnnounce)

    const jiraHandler = new lambda.Function(this, "SupportBotJiraStatusFn", {
      runtime: lambda.Runtime.PROVIDED,
      code: lambda.Code.fromAsset('../.holy-lambda/build/latest.zip'),
      handler: "org.singularity-group.bot-announce.core.JiraStatusLambda",
    });

    const JiraStatusMethod = new apigateway.LambdaIntegration(jiraHandler);
    const jiraResource = api.root.addResource("jira-status", {
      defaultIntegration: JiraStatusMethod
    })
    jiraResource.addMethod("POST", JiraStatusMethod)

  }
}
