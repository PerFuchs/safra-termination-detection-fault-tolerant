import csv
import sys
from collections import defaultdict

from os import listdir

from os.path import isdir, basename, isfile
from pprint import pprint

import plotly
import plotly.graph_objs as go

import scipy.stats as st

from compare_safra_versions import compare_safra_versions
from graphing import get_scatter_graph_with_mean_and_confidence_interval, get_box_trace
from influence_network_size import analyse_influence_of_network_size
from influence_of_faults import analyse_influence_of_faults
from read_results import get_configurations

experiment_folder = sys.argv[1]
configurations = get_configurations(experiment_folder)

expected_configurations = {}

for network_size in [50, 250, 500, 1000, 2000]:
    for fault_group in ['0 fs', '0', '5n', '90']:
        expected_configurations[(network_size, fault_group)] = 40

for c in configurations:
    expected_configurations[(c.number_of_nodes, c.fault_group)] = expected_configurations[(c.number_of_nodes, c.fault_group)] - len(c.repetitions)
    print("+++++++++++++++++++++++++++++++++++++++++++++++++++")
    print("Nodes: %i Fault Percentage: %f Fault Sensitive: %r" % (c.number_of_nodes, c.fault_percentage, c.fault_sensitive))
    print("Repetitions: %i \nInvalid Repetitions: %i" % (len(c.repetitions), len(c.invalid_repetitions)))

    print("Errors:")
    for r in c.invalid_repetitions:
        print("Repetition: %s" % r.folder)
        for e in r.errors:
            print("  " + e)
    print("")
    print("")

pprint(expected_configurations, indent=2)

compare_safra_versions(configurations)
analyse_influence_of_network_size(configurations)

fields = ['tokens', 'tokens_after_termination', 'number_of_nodes_crashed', 'safra_times', 'basic_times',
          'safra_times_after_termination', 'total_times', 'token_bytes']
data = defaultdict(lambda: list())

for f in fields:
    for c in configurations:
        data[f].append(get_box_trace(getattr(c, 'get_' + f)(),
                                     "%i-%f-%r" % (c.number_of_nodes, c.fault_percentage, c.fault_sensitive)))

# for plot_name, plot_data in data.items():
#     plotly.offline.plot(plot_data, filename='../graphs/%s.html' % plot_name)

# for configuration in merged_configurations:
#     data = get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_tokens(), "tokens")


# data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_number_of_nodes_crashed(), "faults")
# data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_backup_tokens(), "backup")
# data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_tokens_after_termination(), "afterTermination")
# data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_safra_time(), "times")
# plotly.offline.plot(data, filename='../graphs/graph.html')
