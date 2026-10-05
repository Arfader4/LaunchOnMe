#!/bin/bash
# Paczka zmian dla Claude (środowisko w chmurze → komputer): zmienione + nowe pliki względem kopii w-orig.
# Użycie (w kontenerze, katalog nad w/ i w-orig/):  bash w/tools/claude-pack.sh N
# Wynik: /mnt/user-data/outputs/batchN.tgz (pliki) i origN.md5 (sumy oryginałów — kontrola, że nikt ich nie zmienił).
X=$1
changed=$(diff -rq w-orig w | grep "^Files" | awk '{print $4}' | sed 's|^w/||')
new=$(diff -rq w-orig w | grep "^Only in w[:/]" | sed -E 's|^Only in w/?([^:]*): (.*)$|\1/\2|; s|^/||')
mkdir -p /mnt/user-data/outputs
(cd w-orig && md5sum $changed > /mnt/user-data/outputs/orig$X.md5)
(cd w && tar czf /mnt/user-data/outputs/batch$X.tgz $changed $new)
echo "changed: $(echo $changed | wc -w) new: $new"
