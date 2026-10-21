#!/usr/bin/env python3
"""
tools/verify_qodo.py

Deterministic verification script for Qodo PR code reviews.
Validates that:
  1. Qodo has completed reviewing the latest HEAD commit of the PR.
  2. Bugs == 0, Rule violations == 0 in Qodo's review summary.
  3. Every review thread started by Qodo has isResolved == true.

Usage:
  python tools/verify_qodo.py [--pr <PR_NUMBER>] [--wait] [--timeout <SECONDS>]
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
import urllib.error

DEFAULT_PROXY = 'http://127.0.0.1:10809'
REPO_OWNER = 'tommynok'
REPO_NAME = 'FreezeYou'

def build_opener():
    proxies = {}
    if os.environ.get('HTTP_PROXY'):
        proxies['http'] = os.environ['HTTP_PROXY']
    elif DEFAULT_PROXY:
        proxies['http'] = DEFAULT_PROXY
    if os.environ.get('HTTPS_PROXY'):
        proxies['https'] = os.environ['HTTPS_PROXY']
    elif DEFAULT_PROXY:
        proxies['https'] = DEFAULT_PROXY

    handler = urllib.request.ProxyHandler(proxies)
    return urllib.request.build_opener(handler)

def get_token():
    token = os.environ.get('GITHUB_TOKEN') or os.environ.get('GH_TOKEN')
    if token:
        return token
    # Try local git config
    try:
        token = subprocess.check_output(
            ['git', 'config', '--get', 'github.token'],
            text=True
        ).strip()
        if token:
            return token
    except Exception:
        pass
    # Try token file in user profile
    token_file = os.path.expanduser('~/.github_token')
    if os.path.isfile(token_file):
        with open(token_file, 'r', encoding='utf-8') as f:
            token = f.read().strip()
            if token:
                return token
    return None

def gh_api_get(opener, token, url):
    headers = {
        'User-Agent': 'FreezeYou-Qodo-Verifier',
        'Accept': 'application/vnd.github.v3+json'
    }
    if token:
        headers['Authorization'] = f'Bearer {token}'
    req = urllib.request.Request(url, headers=headers)
    with opener.open(req, timeout=20) as resp:
        return json.loads(resp.read().decode('utf-8'))

def gh_graphql(opener, token, query, variables):
    headers = {
        'User-Agent': 'FreezeYou-Qodo-Verifier',
        'Content-Type': 'application/json'
    }
    if token:
        headers['Authorization'] = f'Bearer {token}'
    req = urllib.request.Request(
        'https://api.github.com/graphql',
        data=json.dumps({'query': query, 'variables': variables}).encode('utf-8'),
        headers=headers
    )
    with opener.open(req, timeout=20) as resp:
        return json.loads(resp.read().decode('utf-8'))

def get_pr_info(opener, token, pr_num):
    url = f'https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/pulls/{pr_num}'
    return gh_api_get(opener, token, url)

def get_pr_threads(opener, token, pr_num):
    query = """
    query($owner: String!, $name: String!, $pr: Int!) {
      repository(owner: $owner, name: $name) {
        pullRequest(number: $pr) {
          reviewThreads(first: 50) {
            nodes {
              id
              isResolved
              comments(first: 10) {
                nodes {
                  author { login }
                  body
                  path
                  line
                }
              }
            }
          }
        }
      }
    }
    """
    res = gh_graphql(opener, token, query, {'owner': REPO_OWNER, 'name': REPO_NAME, 'pr': pr_num})
    return res['data']['repository']['pullRequest']['reviewThreads']['nodes']

def get_pr_issue_comments(opener, token, pr_num):
    url = f'https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/issues/{pr_num}/comments?per_page=100'
    return gh_api_get(opener, token, url)

def check_qodo_status(opener, token, pr_num):
    pr_data = get_pr_info(opener, token, pr_num)
    head_sha = pr_data['head']['sha']
    head_short = head_sha[:7]

    comments = get_pr_issue_comments(opener, token, pr_num)
    qodo_comments = [
        c for c in comments
        if 'qodo' in c['user']['login'].lower() or 'codium' in c['user']['login'].lower()
    ]

    review_comment = None
    for c in reversed(qodo_comments):
        if 'Code Review by Qodo' in c.get('body', ''):
            review_comment = c
            break

    latest_reviewed_sha = None
    for c in reversed(qodo_comments):
        body = c.get('body', '')
        m = re.findall(r'/commit/([0-9a-f]{7,40})', body)
        if m:
            latest_reviewed_sha = m[-1]
            break

    threads = get_pr_threads(opener, token, pr_num)
    qodo_threads = []
    for t in threads:
        author = t['comments']['nodes'][0]['author']['login'].lower()
        if 'qodo' in author or 'codium' in author:
            qodo_threads.append(t)

    unresolved_threads = [t for t in qodo_threads if not t['isResolved']]

    sha_aligned = False
    if review_comment:
        rev_body = review_comment.get('body', '')
        if head_sha in rev_body or head_short in rev_body:
            sha_aligned = True
        elif latest_reviewed_sha and (head_sha.startswith(latest_reviewed_sha) or latest_reviewed_sha.startswith(head_short)):
            sha_aligned = True

    bugs_count = None
    violations_count = None
    if review_comment:
        b_match = re.search(r'Bugs\s*\(([0-9]+)\)', review_comment['body'])
        if b_match:
            bugs_count = int(b_match.group(1))
        v_match = re.search(r'Rule violations\s*\(([0-9]+)\)', review_comment['body'])
        if v_match:
            violations_count = int(v_match.group(1))

    return {
        'head_sha': head_sha,
        'head_short': head_short,
        'latest_reviewed_sha': latest_reviewed_sha,
        'sha_aligned': sha_aligned,
        'review_comment_found': review_comment is not None,
        'bugs_count': bugs_count,
        'violations_count': violations_count,
        'total_qodo_threads': len(qodo_threads),
        'unresolved_threads': unresolved_threads,
        'review_comment_body': review_comment['body'] if review_comment else ''
    }

def verify_pr(pr_num, wait=False, timeout=300, poll_interval=15):
    opener = build_opener()
    token = get_token()
    start_time = time.time()

    print(f"=== Checking Qodo Review Status for PR #{pr_num} ===")

    while True:
        status = check_qodo_status(opener, token, pr_num)
        head_short = status['head_short']
        rev_sha = status['latest_reviewed_sha'] or 'unknown'

        print(f"[{time.strftime('%X')}] HEAD: {head_short} | Qodo reviewed commit: {rev_sha[:7]} | Aligned: {status['sha_aligned']}")

        if not status['review_comment_found']:
            print("  -> No Qodo Code Review comment found yet.")
        else:
            print(f"  -> Bugs: {status['bugs_count']}, Rule violations: {status['violations_count']}, Unresolved threads: {len(status['unresolved_threads'])}")

        is_done = False
        if status['sha_aligned']:
            if status['bugs_count'] is not None and status['violations_count'] is not None:
                is_done = True

        if is_done:
            if status['bugs_count'] == 0 and status['violations_count'] == 0 and len(status['unresolved_threads']) == 0:
                print(f"\n[PASS] Qodo review is 100% clean for commit {head_short}!")
                print(f"  - Bugs: 0")
                print(f"  - Rule violations: 0")
                print(f"  - Unresolved review threads: 0 / {status['total_qodo_threads']}")
                return 0
            else:
                print(f"\n[FAIL] Qodo detected issues on commit {head_short}:")
                if status['bugs_count'] != 0:
                    print(f"  - Bugs detected: {status['bugs_count']}")
                if status['violations_count'] != 0:
                    print(f"  - Rule violations detected: {status['violations_count']}")
                if status['unresolved_threads']:
                    print(f"  - Unresolved threads ({len(status['unresolved_threads'])}):")
                    for t in status['unresolved_threads']:
                        node = t['comments']['nodes'][0]
                        first_line = node['body'].splitlines()[0] if node['body'] else ''
                        print(f"    * {node.get('path')}:{node.get('line')}: {first_line[:120]}")
                return 1

        if not wait:
            print("\n[WAIT] Qodo has not completed reviewing the latest commit yet. Run with --wait to wait automatically.")
            return 2

        if time.time() - start_time > timeout:
            print(f"\n[TIMEOUT] Reached timeout of {timeout}s waiting for Qodo review on {head_short}.")
            return 3

        time.sleep(poll_interval)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="Verify Qodo review status on a PR.")
    parser.add_argument('--pr', type=int, required=True, help="PR number to verify")
    parser.add_argument('--wait', action='store_true', help="Wait/poll until Qodo review completes")
    parser.add_argument('--timeout', type=int, default=300, help="Max seconds to wait (default 300)")
    parser.add_argument('--interval', type=int, default=15, help="Poll interval in seconds (default 15)")
    args = parser.parse_args()

    sys.exit(verify_pr(args.pr, wait=args.wait, timeout=args.timeout, poll_interval=args.interval))
