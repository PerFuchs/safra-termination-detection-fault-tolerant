import csv
import sys
from collections import defaultdict

from os import listdir

from os.path import isdir, basename, isfile

import plotly
import plotly.graph_objs as go

import scipy.stats as st

from compare_safra import compare_safra_versions
from graphing import get_scatter_graph_with_mean_and_confidence_interval, get_box_trace
from read_results import get_configurations

experiment_folder = sys.argv[1]
configurations = get_configurations(experiment_folder)

for c in configurations:
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

compare_safra_versions(configurations)

# fields = ['tokens', 'tokens_after_termination', 'backup_tokens', 'number_of_nodes_crashed', 'safra_time', 'basic_times',
#           'safra_times_after_termination', 'total_times', 'token_bytes']
# data = defaultdict(lambda: list())
#
# for f in fields:
#     for c in configurations:
#         data[f].append(get_box_trace(getattr(c, 'get_' + f)(),
#                                      "%i-%f-%r" % (c.number_of_nodes, c.fault_percentage, c.fault_sensitive)))
#
# for plot_name, plot_data in data.items():
#     plotly.offline.plot(plot_data, filename='../graphs/%s.html' % plot_name)

# for configuration in merged_configurations:
#     data = get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_tokens(), "tokens")


# data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_number_of_nodes_crashed(), "faults")
# data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_backup_tokens(), "backup")
# data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_tokens_after_termination(), "afterTermination")
# data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_safra_time(), "times")
# plotly.offline.plot(data, filename='../graphs/graph.html')
