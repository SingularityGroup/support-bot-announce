import ec2 = require('@aws-cdk/aws-ec2');
import ecs = require('@aws-cdk/aws-ecs');
import cdk = require('@aws-cdk/core');
import * as apigateway from '@aws-cdk/aws-apigateway';
import * as lambda from '@aws-cdk/aws-lambda';


export class SupportBotVersionAnnounceStack extends cdk.Stack {
  constructor(scope: cdk.Construct, id: string, props?: cdk.StackProps)  {

    super(scope, id, props);

    // The code that defines your stack goes here

    const handler = new lambda.Function(this, "SupportBotVersionAnnounceFn", {
      runtime: lambda.Runtime.PROVIDED,
      code: lambda.Code.fromAsset('../.holy-lambda/build/latest.zip'),
      handler: "org.singularity-group.bot-announce.core.BotAnnounceLambda",
    });

    const api = new apigateway.RestApi(this, "support-bot-jira-status", {
      restApiName: "Support Bot Jira Status Webhook",
      description: "Send message to discord channels when ticket status changes."
    });

    const SayHelloMethod = new apigateway.LambdaIntegration(handler);
    api.root.addMethod("POST", SayHelloMethod);

  }
}
