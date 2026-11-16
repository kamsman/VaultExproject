"""Prouve qu'une implementation du « sighash » est juste, SANS reseau.

L'idee : prendre une transaction P2PKH REELLE, deja signee et acceptee par
la chaine. Son scriptSig contient la signature ET la cle publique. Si notre
calcul du sighash est juste, la signature doit se verifier contre lui.

Si le calcul est faux, la verification echoue — il n'y a pas de demi-mesure
ni de coincidence possible.

Le script de verrouillage de la sortie depensee n'a PAS besoin d'etre
telecharge : pour du P2PKH il se reconstruit depuis la cle publique du
scriptSig, c'est 76a914 + hash160(cle) + 88ac.
"""
import hashlib, sys, json, re

P  = 2**256 - 2**32 - 977
N  = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
GX = 0x79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798
GY = 0x483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8

def add(p, q):
    if p is None: return q
    if q is None: return p
    if p[0] == q[0] and (p[1] + q[1]) % P == 0: return None
    if p == q: l = (3*p[0]*p[0]) * pow(2*p[1], P-2, P) % P
    else:      l = (q[1]-p[1]) * pow(q[0]-p[0], P-2, P) % P
    x = (l*l - p[0] - q[0]) % P
    return (x, (l*(p[0]-x) - p[1]) % P)

def mul(k, p=(GX, GY)):
    r = None
    while k:
        if k & 1: r = add(r, p)
        p = add(p, p); k >>= 1
    return r

def decompresse(b):
    x = int.from_bytes(b[1:], 'big')
    y = pow((x*x*x + 7) % P, (P+1)//4, P)
    if (y & 1) != (b[0] & 1): y = P - y
    return (x, y)

def verifier(sig_der, cle_pub, h):
    """Verification ECDSA classique."""
    assert sig_der[0] == 0x30
    i = 2
    assert sig_der[i] == 0x02; lr = sig_der[i+1]
    r = int.from_bytes(sig_der[i+2:i+2+lr], 'big'); i += 2+lr
    assert sig_der[i] == 0x02; ls = sig_der[i+1]
    s = int.from_bytes(sig_der[i+2:i+2+ls], 'big')
    if not (1 <= r < N and 1 <= s < N): return False
    z = int.from_bytes(h, 'big')
    w = pow(s, N-2, N)
    pt = add(mul(z*w % N), mul(r*w % N, decompresse(cle_pub) if len(cle_pub)==33
                               else (int.from_bytes(cle_pub[1:33],'big'),
                                     int.from_bytes(cle_pub[33:],'big'))))
    return pt is not None and pt[0] % N == r

def sha256d(b):
    return hashlib.sha256(hashlib.sha256(b).digest()).digest()

def hash160(b):
    return hashlib.new('ripemd160', hashlib.sha256(b).digest()).digest()

# ─── serialisation, identique a DogeTransaction.kt ──────────────────────
def e32(n): return bytes([(n >> (8*i)) & 0xFF for i in range(4)])
def e64(n): return bytes([(n >> (8*i)) & 0xFF for i in range(8)])
def varint(n):
    if n < 0xFD: return bytes([n])
    if n <= 0xFFFF: return b'\xfd' + n.to_bytes(2,'little')
    if n <= 0xFFFFFFFF: return b'\xfe' + e32(n)
    return b'\xff' + e64(n)

def serialiser(tx):
    o = e32(tx['version']) + varint(len(tx['entrees']))
    for e in tx['entrees']:
        o += e['prevout'] + e32(e['index']) + varint(len(e['script'])) + e['script'] + e32(e['seq'])
    o += varint(len(tx['sorties']))
    for s in tx['sorties']:
        o += e64(s['valeur']) + varint(len(s['script'])) + s['script']
    return o + e32(tx['locktime'])

def analyser(b):
    i = [0]
    def pren(n):
        r = b[i[0]:i[0]+n]; i[0] += n; return r
    def vi():
        n = b[i[0]]; i[0] += 1
        if n < 0xFD: return n
        if n == 0xFD: return int.from_bytes(pren(2),'little')
        if n == 0xFE: return int.from_bytes(pren(4),'little')
        return int.from_bytes(pren(8),'little')
    tx = {'version': int.from_bytes(pren(4),'little'), 'entrees': [], 'sorties': []}
    for _ in range(vi()):
        pv = pren(32); ix = int.from_bytes(pren(4),'little')
        sc = pren(vi()); sq = int.from_bytes(pren(4),'little')
        tx['entrees'].append({'prevout': pv, 'index': ix, 'script': sc, 'seq': sq})
    for _ in range(vi()):
        val = int.from_bytes(pren(8),'little'); sc = pren(vi())
        tx['sorties'].append({'valeur': val, 'script': sc})
    tx['locktime'] = int.from_bytes(pren(4),'little')
    tx['_consomme'] = i[0]
    return tx

SIGHASH_ALL = 1

def sighash(tx, rang, script_verrou):
    """Ce qui est reellement signe pour l'entree [rang]."""
    copie = {
        'version': tx['version'], 'locktime': tx['locktime'],
        'sorties': tx['sorties'],
        'entrees': [
            {**e, 'script': (script_verrou if k == rang else b'')}
            for k, e in enumerate(tx['entrees'])
        ],
    }
    return sha256d(serialiser(copie) + e32(SIGHASH_ALL))

def decouper_scriptsig(sc):
    """[longueur][signature+type][longueur][cle publique]"""
    n = sc[0]; sig = sc[1:1+n]
    m = sc[1+n]; cle = sc[2+n:2+n+m]
    return sig[:-1], sig[-1], cle

def main(chemin):
    d = json.load(open(chemin, encoding='utf-8'))
    brut = bytes.fromhex(d['hex'])
    tx = analyser(brut)
    print(f"transaction {d['hash'][:16]}…  {len(brut)} octets, "
          f"{len(tx['entrees'])} entree(s), {len(tx['sorties'])} sortie(s)")
    print(f"octets consommes a l'analyse : {tx['_consomme']}/{len(brut)}",
          "✓" if tx['_consomme'] == len(brut) else "✗")
    print(f"reserialisation identique    :",
          "✓" if serialiser(tx) == brut else "✗")
    print()
    verifiees = sautees = 0
    for rang, e in enumerate(tx['entrees']):
        if not e['script']:
            print(f"  entree {rang} : script vide (coinbase ?) — rien a verifier"); sautees += 1; continue
        try:
            sig, type_sig, cle = decouper_scriptsig(e['script'])
        except Exception as ex:
            print(f"  entree {rang} : scriptSig non P2PKH ({ex}) — sautee"); sautees += 1; continue
        if type_sig != SIGHASH_ALL:
            print(f"  entree {rang} : type de signature {type_sig}, pas SIGHASH_ALL — sautee"); sautees += 1; continue
        verrou = b'\x76\xa9\x14' + hash160(cle) + b'\x88\xac'
        h = sighash(tx, rang, verrou)
        ok = verifier(sig, cle, h)
        print(f"  entree {rang} : signature {'VERIFIEE ✓' if ok else 'REFUSEE ✗'}"
              f"   sighash {h.hex()[:16]}…")
        verifiees += ok
    print()
    if verifiees:
        print(f"{verifiees} signature(s) verifiee(s) — le calcul du sighash est JUSTE.")
    else:
        print("aucune signature verifiee — vecteur inutilisable, ou calcul faux.")

if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'doge-mesures.json')
