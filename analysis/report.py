import sys
from collections import defaultdict

from pprint import pprint
import plotly

from report.compare_safra_versions import compare_safra_versions
from report.graphing import get_box_trace
from report.influence_network_size import analyse_influence_of_network_size
from report.influence_of_faults import analyse_influence_of_faults
from read_results import get_configurations


experiment_folder = sys.argv[1]
algorithm = sys.argv[2]
configurations = get_configurations(experiment_folder)

expected_configurations = {}

for network_size in [50, 250, 500, 1000, 2000]:
  for fault_group in ['0 fs', '0', '5n', '90']:
    expected_configurations[(network_size, fault_group)] = 50

for c in configurations:
  expected_configurations[(c.number_of_nodes, c.fault_group)] = expected_configurations[
                                                                  (c.number_of_nodes, c.fault_group)] - len(
    c.repetitions)
  print("+++++++++++++++++++++++++++++++++++++++++++++++++++")
  print(
    "Nodes: %i Fault Percentage: %f Fault Sensitive: %r" % (c.number_of_nodes, c.fault_percentage, c.fault_sensitive))
  print("Repetitions: %i \nInvalid Repetitions: %i" % (len(c.repetitions), len(c.invalid_repetitions)))
  for r in c.repetitions + c.invalid_repetitions:
    if r.errors or r.reanalysis_errors:
      print("Repetition: %s" % r.folder)
      r.print_errors()
    if r.warnings:
      print(r.warnings)
  print("Other runs with warnings %i / with reanalysis warnings %i" % (
  len(list(filter(lambda r: r.warnings, c.repetitions))),
  len(list(filter(lambda r: r.reanalysis_warnings, c.repetitions)))))
  print("")
  print("")

repetitions_with_crashes = 0
repetitions_with_rea_warnings = 0
for c in configurations:
  if c.fault_percentage > 0.0:
    repetitions_with_crashes += len(c.repetitions)
    repetitions_with_rea_warnings += len(list(filter(lambda r: len(r.reanalysis_warnings), c.repetitions)))

print('Runs with crashes: %i' % repetitions_with_crashes)
print("Estimated ratio of repetition with early official termination: %f" % (
      repetitions_with_rea_warnings / repetitions_with_crashes))

pprint(expected_configurations, indent=2)

compare_safra_versions(configurations, algorithm)
analyse_influence_of_network_size(configurations)
analyse_influence_of_faults(configurations, algorithm)

fields = ['tokens', 'tokens_after_termination', 'number_of_nodes_crashed', 'safra_times', 'basic_times',
          'safra_times_after_termination', 'total_times', 'token_bytes', 'backup_tokens']
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
