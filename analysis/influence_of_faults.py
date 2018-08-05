import statistics
from collections import defaultdict, OrderedDict

import plotly

import graphing
from utils import write_csv


def analyse_influence_of_faults(configurations):
  configurations = filter(lambda c: not c.fault_sensitive, configurations)

  grouped_by_fault_group = defaultdict(lambda: list())
  for c in configurations:
    grouped_by_fault_group[c.fault_group].append(c)
  grouped_by_fault_group_sorted = dict(
    map(lambda i: (i[0], sorted(i[1], key=lambda c: c.number_of_nodes)), grouped_by_fault_group.items()))
  grouped_by_fault_group_sorted = OrderedDict(
    sorted(grouped_by_fault_group_sorted.items(), key=lambda i: i[0]))

  present_token_and_token_after_termination(grouped_by_fault_group_sorted)
  present_token_table(grouped_by_fault_group_sorted)
  present_total_times_table(grouped_by_fault_group_sorted)
  present_processing_times_table(grouped_by_fault_group_sorted)

  present_processing_times(grouped_by_fault_group_sorted)
  present_total_times(grouped_by_fault_group_sorted)


def present_processing_times_table(configurations):
  headers = ['networkSize', 'noFaults', 'fiveN', 'differenceFiveN', 'ninety', 'differenceNinety']
  rows = []
  network_sizes = 4
  for i in range(network_sizes):
    c_0 = configurations['0'][i]
    c_5n = configurations['5n'][i]
    c_90 = configurations['90'][i]

    no_faults_mean = round(statistics.mean(c_0.get_safra_times()), 3)
    no_faults_mean_after = round(statistics.mean(c_0.get_safra_times_after_termination()), 3)
    no_faults_mean_col = '%.03f (%.03f)' % (no_faults_mean, no_faults_mean_after)

    mean_5n = round(statistics.mean(c_5n.get_safra_times()), 3)
    mean_5n_after = round(statistics.mean(c_5n.get_safra_times_after_termination()), 3)
    mean_5n_col = '%.03f (%.03f)' % (mean_5n, mean_5n_after)

    mean_90 = round(statistics.mean(c_90.get_safra_times()), 3)
    mean_90_after = round(statistics.mean(c_90.get_safra_times_after_termination()), 3)
    mean_90_col = '%.03f (%.03f)' % (mean_90, mean_90_after)

    overhead_5n = round(mean_5n / no_faults_mean, 2)
    overhead_5n_after = round(mean_5n_after / no_faults_mean_after, 2)
    overhead_5n_col = '%.02f (%.02f)' % (overhead_5n, overhead_5n_after)

    overhead_90 = round(mean_90 / no_faults_mean, 2)
    overhead_90_after = round(mean_90_after / no_faults_mean_after, 2)
    overhead_90_col = '%.02f (%.02f)' % (overhead_90, overhead_90_after)

    rows.append([c_0.number_of_nodes,
                 no_faults_mean_col, mean_5n_col, overhead_5n_col,
                 mean_90_col,
                 overhead_90_col])

  write_csv('../report/figures/processing-times-faulty.csv', headers, rows)


def present_total_times_table(configurations):
  headers = ['networkSize', 'noFaults', 'fiveN', 'differenceFiveN', 'ninety', 'differenceNinety']

  network_sizes = 4
  rows = []
  for i in range(network_sizes):
    c_0 = configurations['0'][i]
    c_5n = configurations['5n'][i]
    c_90 = configurations['90'][i]

    mean_0 = round(statistics.mean(c_0.get_average_total_times()), 3)
    mean_0_after = round(statistics.mean(c_0.get_total_times_after_termination()), 3)
    mean_0_col = '%.03f (%.03f)' % (mean_0, mean_0_after)

    mean_5n = round(statistics.mean(c_5n.get_average_total_times()), 3)
    mean_5n_after = round(statistics.mean(c_5n.get_total_times_after_termination()), 3)
    mean_5n_col = '%.03f (%.03f)' % (mean_5n, mean_5n_after)

    mean_90 = round(statistics.mean(c_90.get_average_total_times()), 3)
    mean_90_after = round(statistics.mean(c_90.get_total_times_after_termination()), 3)
    mean_90_col = '%.03f (%.03f)' % (mean_90, mean_90_after)

    overhead_5n = round(mean_5n / mean_0, 2)
    overhead_5n_after = round(mean_5n_after / mean_0_after, 2)
    overhead_5n_col = '%.03f (%.03f)' % (overhead_5n, overhead_5n_after)

    overhead_90 = round(mean_90 / mean_0, 2)
    overhead_90_after = round(mean_90_after / mean_0_after, 2)
    overhead_90_col = '%.03f (%.03f)' % (overhead_90, overhead_90_after)

    rows.append([c_0.number_of_nodes,
                 mean_0_col,
                 mean_5n_col, overhead_5n_col,
                 mean_90_col, overhead_90_col])

  write_csv('../report/figures/total-times-faulty.csv', headers, rows)


def present_token_table(configurations):
  headers = ['networkSize', 'noFaults', 'fiveN', 'differenceFiveN', 'ninety', 'differenceNinety']

  network_sizes = 4
  rows = []
  for i in range(network_sizes):
    c_0 = configurations['0'][i]
    c_5n = configurations['5n'][i]
    c_90 = configurations['90'][i]

    mean_0 = round(statistics.mean(c_0.get_tokens()))
    mean_0_after = round(statistics.mean(c_0.get_tokens_after_termination()))
    mean_0_col = '%i (%i)' % (mean_0, mean_0_after)

    mean_5n = round(statistics.mean(c_5n.get_tokens()))
    mean_5n_after = round(statistics.mean(c_5n.get_tokens_after_termination()))
    mean_5n_col = '%i (%i)' % (mean_5n, mean_5n_after)

    mean_90 = round(statistics.mean(c_90.get_tokens()))
    mean_90_after = round(statistics.mean(c_90.get_tokens_after_termination()))
    mean_90_col = '%i (%i)' % (mean_90, mean_90_after)

    overhead_5n = round(mean_5n / mean_0, 2)
    overhead_5n_after = round(mean_5n_after / mean_0_after, 2)
    overhead_5n_col = '%.02f (%.02f)' % (overhead_5n, overhead_5n_after)

    overhead_90 = round(mean_90 / mean_0, 2)
    overhead_90_after = round(mean_90_after / mean_0_after, 2)
    overhead_90_col = '%.02f (%.02f)' % (overhead_90, overhead_90_after)

    rows.append([c_0.number_of_nodes,
                 mean_0_col,
                 mean_5n_col, overhead_5n_col,
                 mean_90_col, overhead_90_col])

  write_csv('../report/figures/tokens-faulty.csv', headers, rows)


def present_processing_times(configurations):
  data = []

  for fault_group, configurations_sorted in configurations.items():
    for i, c in enumerate(configurations_sorted):
      data.append(graphing.get_box_trace(c.get_basic_times(), 'B %s %i' % (fault_group, c.number_of_nodes)))
      data.append(graphing.get_box_trace(c.get_safra_times(), 'T %s %i' % (fault_group, c.number_of_nodes)))
      data.append(graphing.get_box_trace(c.get_safra_times_after_termination(),
                                         'T %s %i' % (fault_group, c.number_of_nodes), 'rgb(255,140,0)'))

  plotly.offline.plot(data, filename='../graphs/processing_times.html')


def present_total_times(configurations):
  data = []

  for fault_group, configurations_sorted in configurations.items():
    for i, c in enumerate(configurations_sorted):
      data.append(graphing.get_box_trace(c.get_average_total_times(), 'T %s %i' % (fault_group, c.number_of_nodes)))
      data.append(graphing.get_box_trace(c.get_total_times_after_termination(),
                                         'T %s %i' % (fault_group, c.number_of_nodes), 'rgb(255,140,0)'))

  plotly.offline.plot(data, filename='../graphs/total_times.html')


def present_token_and_token_after_termination(configurations):
  data = []

  for fault_group, configurations_sorted in configurations.items():
    for i, c in enumerate(configurations_sorted):
      data.append(graphing.get_box_trace(c.get_tokens(), 'T %s %i' % (fault_group, c.number_of_nodes)))
      data.append(graphing.get_box_trace(c.get_tokens_after_termination(),
                                         'T %s %i' % (fault_group, c.number_of_nodes), 'rgb(255,140,0)'))
      if fault_group == '90':
        data.append(graphing.get_box_trace(c.get_backup_tokens(),
                                           'T %s %i' % (fault_group, c.number_of_nodes), 'rgb(140,255,0)'))

  plotly.offline.plot(graphing.hide_layout(data), filename='../graphs/tokens_and_tokens_after_faulty.html')
