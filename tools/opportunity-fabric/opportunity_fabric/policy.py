from __future__ import annotations

# Conservative policy gate: "blocked" means the orchestrator will not deploy mining there.
# "review" means explicit provider permission must be verified first.
POLICIES = {
    'local': {
        'mining': 'allowed',
        'reason': 'User-owned hardware; platform ToS gate does not apply.',
    },
    'google_colab': {
        'mining': 'blocked',
        'reason': 'Colab managed runtimes explicitly prohibit cryptocurrency mining.',
        'source': 'https://research.google.com/colaboratory/faq.html',
    },
    'kaggle': {
        'mining': 'blocked',
        'reason': 'Kaggle Terms explicitly prohibit cryptomining.',
        'source': 'https://www.kaggle.com/terms',
    },
    'github_actions': {
        'mining': 'blocked',
        'reason': 'GitHub Actions explicitly prohibits cryptomining.',
        'source': 'https://docs.github.com/en/site-policy/github-terms/github-terms-for-additional-products-and-features',
    },
    'oracle_free': {
        'mining': 'blocked',
        'reason': 'Excluded conservatively: recent OCI Free Tier coin-mining suspensions are reported; do not risk the tenancy without explicit written permission.',
    },
    'vast_ai': {
        'mining': 'review',
        'reason': 'Vast.ai has restrictions around credits/payment sources for cryptocurrency mining; verify the exact account/credit terms before use.',
    },
    'unknown_cloud': {
        'mining': 'review',
        'reason': 'Provider terms have not been verified. No mining deployment until they are.',
    },
}

def mining_allowed(provider: str) -> tuple[bool, str]:
    p = POLICIES.get(provider, POLICIES['unknown_cloud'])
    return p['mining'] == 'allowed', p['reason']
