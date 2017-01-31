
import os
import platform
import subprocess

import numpy as np

package_to_test = 'combinedstrategies'
params_to_test = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9']

src_dir = './src/'
# benchmarks, in the format (package name, commit hash, debug params)
# (use a singleton list if no params to try)
benchmarks = [
    ('combinedstrategies', '16a6621f6df0e2c25bfcb1c572d95efe15d40916', ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9']),
    ('turtlebot', '16a6621f6df0e2c25bfcb1c572d95efe15d40916', ['']),
    ('noop', '16a6621f6df0e2c25bfcb1c572d95efe15d40916', [''])
]

maps = [
    '1337Tree',
    'Aligned',
    'Alone',
    'Arena',
    'Barbell',
    'Barrier',
    'Blitzkrieg',
    'Boxed',
    'BugTrap',
    'Bullseye',
    'Captive',
    'Caterpillar',
    'Chess',
    'Chevron',
    'Clusters',
    'Conga',
    'Cramped',
    'CropCircles',
    'Croquembouche',
    'CrossFire',
    'DarkSide',
    'DeathStar',
    'Defenseless',
    'DenseForest',
    'DigMeOut',
    'Enclosure',
    'Fancy',
    'FlappyTree',
    'GiantForest',
    'Grass',
    'GreatDekuTree',
    'GreenHouse',
    'HedgeMaze',
    'HiddenTunnel',
    'HouseDivided',
    'Hurdle',
    'Interference',
    'Lanes',
    'Levels',
    'LilForts',
    'LilMaze',
    'LineOfFire',
    'MagicWood',
    'Maniple',
    'Misaligned',
    'ModernArt',
    'MyFirstMap',
    'OMGTree',
    'Ocean',
    'Oxygen',
    'PacMan',
    'PasscalsTriangles',
    'PeacefulEncounter',
    'Planets',
    'Present',
    'PureImagination',
    'Shortcut',
    'Shrubbery',
    'Slant',
    'Snowflake',
    'SparseForest',
    'Sprinkles',
    'Standoff',
    'TheOtherSide',
    'TicTacToe',
    'TreeFarm',
    'Turtle',
    'Waves',
    'Whirligig',
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

if platform.system() == 'Windows':
    gradle_command = 'gradlew.bat'
else:
    gradle_command = './gradlew'
command = [gradle_command, 'build']
subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

# TODO: use params
num_wins = np.zeros([len(flattened_benchmarks), len(maps), len(params_to_test)])
for i, (generated_package, generated_param) in enumerate(flattened_benchmarks):
    # run the match!
    for j, map_name in enumerate(maps):
        for k in range(runs_per_matchup):
            for m, test_param in enumerate(params_to_test):
                # swap teams, to avoid some kind of weird directional bias
                if k % 2 == 0:
                    teamA = package_to_test
                    paramA = test_param
                    teamB = generated_package
                    paramB = generated_param
                else:
                    teamA = generated_package
                    paramA = generated_param
                    teamB = package_to_test
                    paramB = test_param
                maxInt = (2**31)-1
                seedA = np.random.randint(maxInt)
                seedB = np.random.randint(maxInt)
                command = [gradle_command, 'fastrun', '-PteamA=' + teamA, '-PteamB=' + teamB, '-PparamA=' + paramA, '-PparamB=' + paramB, '-Pmaps=' + map_name,
                    '-PseedA='+str(seedA), '-PseedB='+str(seedB)]
                result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                
                # daniel: normally I get some warnings about L4J logger problems. If there's more errors than that, there might be a problem
                if len(result.stderr) > 213:
                    print('\nEncountered a long error message. is the process running correctly? to reproduce, run:')
                    print(" ".join(command))
                 
                out = result.stdout
                
                matchStartIdx = out.find(b'Match Starting')
                matchEndIdx = out.find(b'Match Finished')
                if out[matchStartIdx:matchEndIdx].count(b'\n') > 4:
                    print('\nEncountered a long stdout. Is a robot printing messages to stdout? to reproduce, run:')
                    print(" ".join(command))            
                
                winsIdx = out.find(b'wins')
                prevNewlineIdx = out.rfind(b'\n', 0, winsIdx)
                nextNewlineIdx = out.find(b'\n', winsIdx)
                winString = out[prevNewlineIdx:nextNewlineIdx].decode('utf-8')
                tokens = winString.split()
                winner = tokens[1]
                if winner == package_to_test:
                    num_wins[i, j, m] += 1.0
                print('.', end='', flush=True)

print()
win_rate = num_wins / runs_per_matchup

print()
# these names are long and ugly, print them first
print('\tBENCHMARK ID MAP')
print('Id\tbenchmark param tuple'.format(i, benchmark))
for i, benchmark in enumerate(flattened_benchmarks):
    print('{}\t{}'.format(i, benchmark))


for m, test_param in enumerate(params_to_test):

    print()
    print('-------------------------------------------------------------------------------------')
    print('-------------------------------------------------------------------------------------')
    print()
    print('\tWIN RATES PER BENCHMARK PER MAP, param=' + test_param)
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
            mean = win_rate[i, j, m]
            print('{:.4f}\t'.format(mean), end='')
        map_mean = np.mean(win_rate[:,j,m])
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


