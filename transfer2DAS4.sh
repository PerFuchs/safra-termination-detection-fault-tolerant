#!/usr/bin/env fish
rsync -av --delete -e 'ssh -o "ProxyCommand ssh -A pfs250@ssh.data.vu.nl -W %h:%p"' --exclude-from ./no-transfer.txt . pfs250@fs0.das4.cs.vu.nl:/home/pfs250/safra/
