
import os
import subprocess

import numpy as np

package_to_test = 'helloworld'
params_to_test = ['']

src_dir = './src/'
# benchmarks, in the format (package name, commit hash, debug params)
# (use a singleton list if no params to try)
benchmarks = [
    ('helloworld', '3ddd89a54e411c400b4f63197596ee7ffb3dc696', ['']),
    ('noop', '3ddd89a54e411c400b4f63197596ee7ffb3dc696', [''])
]

maps = [
    'MOBA',
    'Barrier',
    'DenseForest',
    'Enclosure',
    'Hurdle',
    'SparseForest',
    'shrine',
]

benchmark_prefix = 'benchmark'

runs_per_matchup = 10 # 100 or so would be better

###########################################
print('performing benchmarks for package \'{}\'...'.format(package_to_test))

np.random.seed(2017)

# flatten and checkout
flattened_benchmarks = []
for benchmark in benchmarks:
    orig_package = benchmark[0]
    commit_hash = benchmark[1]
    params = benchmark[2]
    
    package_path = src_dir + orig_package + '/'
    
    generated_package = benchmark_prefix + '_' + orig_package + '_' + commit_hash
    generated_package_path = src_dir + generated_package + '/'
    
    if not os.path.exists(generated_package_path):
        os.makedirs(generated_package_path)
    
    # this gives us a list of files in the directory at the commit
    completed_process = subprocess.run(['git', 'cat-file', '-p',  commit_hash + ':' + package_path], stdout=subprocess.PIPE)
    tokens = completed_process.stdout.split()
    # each line contains 4 tokens only the 4th one is relevant
    for line in range((int)(len(tokens)/4)):
        # for each file, pipe it to sed to replace the package name, then write it to a new directory
        token_num = line*4 + 3
        decoded_name = tokens[token_num].decode('utf-8')
        old_filename = package_path + decoded_name
        new_filename = generated_package_path + decoded_name
        pipe = subprocess.Popen(['git', 'cat-file', '-p',  commit_hash + ':' + old_filename], stdout=subprocess.PIPE)
        with open(new_filename, 'w') as f:
            command = ['sed', '-r', 's/package ' + orig_package + '/package ' + generated_package + '/']
            sed = subprocess.Popen(command, stdin=pipe.stdout, stdout=f)
            pipe.wait()
    
    for param in params:
        flattened_benchmarks.append((generated_package, param))

# TODO: might want to track win conditions and time to victory
# a tiebreaker win at turn 3000 is much less convincing than a domination win at turn 500


# build packages once
command = ['./gradlew', 'build']
subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

# TODO: use params
num_wins = np.zeros([len(flattened_benchmarks), len(maps)])
for i, (generated_package, param) in enumerate(flattened_benchmarks):
    # run the match!
    for j, map_name in enumerate(maps):
        for k in range(runs_per_matchup):
            # swap teams, to avoid some kind of weird directional bias
            if k % 2 == 0:
                teamA = package_to_test
                teamB = generated_package
            else:
                teamA = generated_package
                teamB = package_to_test
            maxInt = (2**31)-1
            seedA = np.random.randint(maxInt)
            seedB = np.random.randint(maxInt)
            command = ['./gradlew', 'fastrun', '-PteamA=' + teamA, '-PteamB=' + teamB, '-Pmaps=' + map_name,
                '-PseedA='+str(seedA), '-PseedB='+str(seedB)]
            result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            
            # daniel: normally I get some warnings about L4J logger problems. If there's more errors than that, there might be a problem
            if len(result.stderr) > 210:
                print('Encountered a long error message. is the process running correctly?')
                print(result.stderr)
             
            out = result.stdout
            
            matchStartIdx = out.find(b'Match Starting') 
            matchEndIdx = out.find(b'Match Finished') 
            if out[matchStartIdx:matchEndIdx].count(b'\n') > 4:
                print('Encountered a long stdout. Is a robot printing messages to stdout?')
                #print(result.stdout)
            
            
            winsIdx = out.find(b'wins')
            prevNewlineIdx = out.rfind(b'\n', 0, winsIdx)
            nextNewlineIdx = out.find(b'\n', winsIdx)
            winString = out[prevNewlineIdx:nextNewlineIdx].decode('utf-8')
            tokens = winString.split()
            winner = tokens[1]
            if winner == package_to_test:
                num_wins[i, j] += 1.0
            print('.', end='', flush=True)

print()
win_rate = num_wins / runs_per_matchup

print()
# these names are long and ugly, print them first
print('\tBENCHMARK ID MAP')
print('Id\tbenchmark param tuple'.format(i, benchmark))
for i, benchmark in enumerate(flattened_benchmarks):
    print('{}\t{}'.format(i, benchmark))

print()
print('-------------------------------------------------------------------------------------')
print()

print('\tWIN RATES PER BENCHMARK PER MAP')
print('\t\t\tbenchmark Id')
print('\t\t', end='')
for i in range(len(flattened_benchmarks)):
    print('\t  {}'.format(i), end='')
print('\toverall')

for j in range(len(maps)):
    if j == 0:
        print('map', end='')
    
    short_map_name = maps[j]
    short_map_name = '{0: <15}'.format(short_map_name)
    if len(short_map_name) > 15:
        short_map_name = short_map_name[:15]
    print('\t{}\t'.format(short_map_name), end='')
    for i in range(len(flattened_benchmarks)):
        mean = win_rate[i, j]
        print('{:.4f}\t'.format(mean), end='')
    map_mean = np.mean(win_rate[:,j])
    print('{:.4f}'.format(map_mean))

print()
print('-------------------------------------------------------------------------------------')
print()

print('\tOVERALL WIN RATES PER BENCHMARKS')

print('Id\twinrate')
for i in range(len(flattened_benchmarks)):
    print('{}\t{:.4f}'.format(i, np.mean(win_rate[i,:])))

print()
print('-------------------------------------------------------------------------------------')
print()

print('OVERALL MEAN WIN RATES: {}'.format(np.mean(win_rate)))

