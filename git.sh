#!/bin/sh
#功能：提交代码时email硬控情况下，批量更新为新邮箱
git filter-branch --env-filter '
OLD_EMAIL="xxx01@qq.com"
CORRECT_NAME="xxx01"
CORRECT_EMAIL="xxx01@163.com"
    if [ "$GIT_COMMITTER_EMAIL" = "$OLD_EMAIL" ] 
	then
        export GIT_COMMITTER_NAME="$CORRECT_NAME"
        export GIT_COMMITTER_EMAIL="$CORRECT_EMAIL"
    fi
    if [ "$GIT_AUTHOR_EMAIL" = "$OLD_EMAIL" ] 
	then
        export GIT_AUTHOR_NAME="$CORRECT_NAME"
        export GIT_AUTHOR_EMAIL="$CORRECT_EMAIL"
    fi
' --tag-name-filter cat -- --branches --tags