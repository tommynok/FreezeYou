#!/usr/bin/env python3
"""
tools/verify_qodo.py

Deterministic verification script for Qodo PR code reviews.
Validates that:
  1. Qodo has completed reviewing the latest HEAD commit of the PR.
  2. Bugs == 0, Rule violations == 0 in Qodo's review summary.
  3. Every review thread started by Qodo has isResolved == true.

Usage:
  python tools/verify_qodo.py [--pr <PR_NUMBER>] [--wait] [--timeout <SECONDS>] [--proxy <URL>]
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

if hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace', line_buffering=True)
    except Exception:
        pass

REPO_OWNER = 'tommynok'
REPO_NAME = 'FreezeYou'

def build_opener(proxy_arg=None):
    proxies = {}
    http_env = os.environ.get('HTTP_PROXY') or os.environ.get('http_proxy')
    https_env = os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy')

    if proxy_arg:
        proxies['http'] = proxy_arg
        proxies['https'] = proxy_arg
    elif http_env or https_env:
        if http_env:
            proxies['http'] = http_env
        if https_env:
            proxies['https'] = https_env
    else:
        # Check if local proxy on 127.0.0.1:10809 is listening
        import socket
        try:
            with socket.create_connection(('127.0.0.1', 10809), timeout=0.3):
                proxies['http'] = 'http://127.0.0.1:10809'
                proxies['https'] = 'http://127.0.0.1:10809'
        except Exception:
            pass

    handler = urllib.request.ProxyHandler(proxies)
    return urllib.request.build_opener(handler)

def get_token():
    token = os.environ.get('GITHUB_TOKEN') or os.environ.get('GH_TOKEN')
    if token:
        return token
    try:
        token = subprocess.check_output(
            ['git', 'config', '--get', 'github.token'],
            text=True
        ).strip()
        if token:
            return token
    except Exception:
        pass
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
        data = json.loads(resp.read().decode('utf-8'))
        link_header = resp.headers.get('Link', '')
        return data, link_header

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
    data, _ = gh_api_get(opener, token, url)
    return data

def get_pr_threads(opener, token, pr_num):
    all_threads = []
    cursor = None
    query = """
    query($owner: String!, $name: String!, $pr: Int!, $cursor: String) {
      repository(owner: $owner, name: $name) {
        pullRequest(number: $pr) {
          reviewThreads(first: 50, after: $cursor) {
            pageInfo {
              hasNextPage
              endCursor
            }
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
    while True:
        res = gh_graphql(opener, token, query, {
            'owner': REPO_OWNER,
            'name': REPO_NAME,
            'pr': pr_num,
            'cursor': cursor
        })
        if not res or not res.get('data'):
            if res and 'errors' in res:
                raise RuntimeError(f"GraphQL error: {res['errors']}")
            raise RuntimeError(f"Empty GraphQL response: {res}")
        repo = res['data'].get('repository') or {}
        pr = repo.get('pullRequest') or {}
        connection = pr.get('reviewThreads')
        if not connection:
            raise RuntimeError(f"Failed to retrieve review threads for PR #{pr_num}")
        all_threads.extend(connection.get('nodes', []))
        page_info = connection.get('pageInfo') or {}
        if page_info.get('hasNextPage'):
            cursor = page_info.get('endCursor')
        else:
            break
    return all_threads

def get_pr_issue_comments(opener, token, pr_num):
    all_comments = []
    page = 1
    while True:
        url = f'https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/issues/{pr_num}/comments?per_page=100&page={page}'
        data, link_header = gh_api_get(opener, token, url)
        if not data:
            break
        all_comments.extend(data)
        if 'rel="next"' not in link_header or len(data) < 100:
            break
        page += 1
    return all_comments

def check_qodo_status(opener, token, pr_num, ignore_findings=None):
    pr_data = get_pr_info(opener, token, pr_num)
    head_sha = pr_data['head']['sha']
    head_short = head_sha[:7]

    comments = get_pr_issue_comments(opener, token, pr_num)
    qodo_comments = [
        c for c in comments
        if 'qodo' in (c.get('user') or {}).get('login', '').lower()
        or 'codium' in (c.get('user') or {}).get('login', '').lower()
    ]

    # Find the main review summary comment
    review_comment = None
    for c in reversed(qodo_comments):
        if 'Code Review by Qodo' in c.get('body', ''):
            review_comment = c
            break

    # Determine which commit the review summary actually covers
    reviewed_sha = None
    if review_comment:
        rev_id = review_comment['id']
        rev_body = review_comment.get('body', '')

        # 1. Check embedded commit comment at the end of the review comment body
        # e.g.: <!-- https://github.com/owner/repo/commit/7a8539a... -->
        m = re.findall(r'/commit/([0-9a-f]{7,40})', rev_body)
        if m:
            reviewed_sha = m[-1]

        # 2. Check for an update comment specifically targeting this review_comment ID
        # e.g.: [Code review](https://...#issuecomment-12345) by qodo was updated up to the latest commit ...
        for c in reversed(qodo_comments):
            c_body = c.get('body', '')
            if f'issuecomment-{rev_id}' in c_body and 'updated up to the latest commit' in c_body:
                up_match = re.search(r'/commit/([0-9a-f]{7,40})', c_body)
                if up_match:
                    reviewed_sha = up_match.group(1)
                    break

    threads = get_pr_threads(opener, token, pr_num)
    qodo_threads = []
    for t in threads:
        nodes = t.get('comments', {}).get('nodes', [])
        if not nodes:
            continue
        first_comment = nodes[0]
        author_login = (first_comment.get('author') or {}).get('login', '').lower()
        if 'qodo' in author_login or 'codium' in author_login:
            qodo_threads.append(t)

    unresolved_threads = [t for t in qodo_threads if not t.get('isResolved', False)]

    # SHA alignment: the review summary MUST be for the current HEAD commit
    sha_aligned = False
    if reviewed_sha:
        if len(reviewed_sha) == len(head_sha):
            sha_aligned = (reviewed_sha == head_sha)
        else:
            sha_aligned = head_sha.startswith(reviewed_sha)

    bugs_count = None
    violations_count = None
    if review_comment:
        b_match = re.search(r'Bugs\s*\(([0-9]+)\)', review_comment['body'])
        if b_match:
            bugs_count = int(b_match.group(1))
        v_match = re.search(r'Rule violations\s*\(([0-9]+)\)', review_comment['body'])
        if v_match:
            violations_count = int(v_match.group(1))

        if bugs_count is None and violations_count is None:
            body = review_comment.get('body', '')
            has_resolved_section = '<h3>Resolved findings</h3>' in body
            has_trimmed_warning = 'Comment size exceeded' in body
            has_clean_marker = bool(re.search(r'\b(no findings|no bugs|no rule violations|all checks passed)\b', body, re.IGNORECASE))
            is_recognized_format = ('Code Review by Qodo' in body) and (has_resolved_section or has_trimmed_warning or has_clean_marker)

            if is_recognized_format:
                if ignore_findings is None:
                    ignore_findings = []
                active_part = body.split('<h3>Resolved findings</h3>')[0] if has_resolved_section else body
                # Match numbered finding headers, e.g. <summary> 1. Title <code>Bug</code></summary>
                headers = re.findall(r'<summary>\s*\d+\.\s*(.*?)</summary>', active_part, re.DOTALL)
                b_cnt = 0
                v_cnt = 0
                for h in headers:
                    if any(ign.lower() in h.lower() for ign in ignore_findings):
                        continue
                    if re.search(r'\bRule violation\b', h, re.IGNORECASE):
                        v_cnt += 1
                    else:
                        b_cnt += 1
                bugs_count = b_cnt
                violations_count = v_cnt

    return {
        'head_sha': head_sha,
        'head_short': head_short,
        'reviewed_sha': reviewed_sha,
        'sha_aligned': sha_aligned,
        'review_comment_found': review_comment is not None,
        'bugs_count': bugs_count,
        'violations_count': violations_count,
        'total_qodo_threads': len(qodo_threads),
        'unresolved_threads': unresolved_threads,
        'review_comment_body': review_comment['body'] if review_comment else ''
    }

def verify_pr(pr_num, wait=False, timeout=300, poll_interval=15, proxy=None, ignore_findings=None):
    opener = build_opener(proxy)
    token = get_token()
    start_time = time.time()

    print(f"=== Checking Qodo Review Status for PR #{pr_num} ===")

    while True:
        try:
            status = check_qodo_status(opener, token, pr_num, ignore_findings=ignore_findings)
        except Exception as e:
            if wait and (time.time() - start_time < timeout):
                print(f"[{time.strftime('%X')}] Warning: transient error during poll ({e}). Retrying in {poll_interval}s...")
                time.sleep(poll_interval)
                continue
            raise
        head_short = status['head_short']
        rev_sha = (status['reviewed_sha'] or 'unknown')[:7]

        print(f"[{time.strftime('%X')}] HEAD: {head_short} | Review covered commit: {rev_sha} | Aligned: {status['sha_aligned']}")

        if not status['review_comment_found']:
            print("  -> No Qodo Code Review comment found yet.")
        else:
            bugs_str = str(status['bugs_count']) if status['bugs_count'] is not None else 'unknown'
            violations_str = str(status['violations_count']) if status['violations_count'] is not None else 'unknown'
            print(f"  -> Bugs: {bugs_str}, Rule violations: {violations_str}, Unresolved threads: {len(status['unresolved_threads'])}")

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
                        nodes = t.get('comments', {}).get('nodes', [])
                        if nodes:
                            first_line = nodes[0].get('body', '').splitlines()[0]
                            print(f"    * {nodes[0].get('path')}:{nodes[0].get('line')}: {first_line[:120]}")
                return 1

        if not wait:
            if not status['sha_aligned']:
                print(f"\n[WAIT] Qodo review has not covered commit {head_short} yet (currently at {rev_sha}). Run with --wait to wait automatically.")
            elif status['bugs_count'] is None or status['violations_count'] is None:
                print(f"\n[FAIL] Qodo review comment format was not recognized or findings counts could not be validated for commit {head_short}.")
                return 1
            else:
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
    parser.add_argument('--proxy', type=str, default=None, help="Explicit HTTP proxy URL")
    parser.add_argument('--ignore-finding', action='append', default=[], help="Finding title to ignore as false positive")
    args = parser.parse_args()

    sys.exit(verify_pr(args.pr, wait=args.wait, timeout=args.timeout, poll_interval=args.interval, proxy=args.proxy, ignore_findings=args.ignore_finding))
