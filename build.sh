#!/bin/sh

bb hl:compile

bb hl:native:executable

cd cdk && cdk deploy
