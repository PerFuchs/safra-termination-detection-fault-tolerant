import statistics
import sys
from collections import defaultdict, OrderedDict
from math import ceil

from utils import write_csv, write_string


def compare_safra_versions(configurations):
  fault_free_configurations = list(filter(lambda c: c.fault_percentage == 0, configurations))

  paired_by_network_size = defaultdict(lambda: [None, None])
  for c in fault_free_configurations:
    paired_by_network_size[c.number_of_nodes][int(c.fault_sensitive)] = c

  paired_by_network_size_sorted = OrderedDict(sorted(paired_by_network_size.items(), key=lambda i: i[0]))

  present_processing_times(paired_by_network_size_sorted)
  present_total_times(paired_by_network_size_sorted)

  compare_token_bytes(paired_by_network_size_sorted)


def present_processing_times(configurations):
  headers = ['networkSize', 'basic', 'FT', 'FS', 'difference', 'FSoverhead', 'FToverhead', 'FSAfter', 'FTAfter']


  max_overhead = 0
  min_overhead = sys.maxsize
  rows = []
  for i, (network_size, (ft_configuration, fs_configuration)) in enumerate(configurations.items()):
    basic_time_mean = round(statistics.mean(ft_configuration.get_basic_times()), 3)

    ft_safra_time_mean = round(statistics.mean(ft_configuration.get_safra_times()), 3)
    ft_safra_time_mean_after = round(statistics.mean(ft_configuration.get_safra_times_after_termination()), 3)

    fs_safra_time_mean = round(statistics.mean(fs_configuration.get_safra_times()), 3)
    fs_safra_time_mean_after = round(statistics.mean(fs_configuration.get_safra_times_after_termination()), 3)

    safra_time_difference = round(ft_safra_time_mean / fs_safra_time_mean, 2)
    ft_overhead = round(ft_safra_time_mean / basic_time_mean * 100, 2)
    fs_overhead = round(fs_safra_time_mean / basic_time_mean * 100, 2)

    max_overhead = max(ft_overhead, max_overhead)
    min_overhead = min(ft_overhead, min_overhead)

    rows.append([network_size,
                 basic_time_mean, ft_safra_time_mean, fs_safra_time_mean,
                 safra_time_difference,
                 fs_overhead, ft_overhead,
                 fs_safra_time_mean_after, ft_safra_time_mean_after])

  write_csv('../report/figures/processing-times.csv', headers, rows)
  write_string('../report/figures/min-ft-processing-time-overhead.txt', '%.02f' % min_overhead)
  write_string('../report/figures/max-ft-processing-time-overhead.txt', '%.02f' % max_overhead)
  write_string('../report/figures/less-than-processing-time-overhead.txt', '%d' % ceil(max_overhead))

def present_total_times(configurations):
  headers = ['networkSize', 'FT', 'FS', 'difference', 'FTAfter', 'FSAfter', 'differenceAfter']

  ratio_250 = -1
  rows = []
  for i, (network_size, (ft_configuration, fs_configuration)) in enumerate(configurations.items()):
    ft_total_time = round(statistics.mean(ft_configuration.get_average_total_times()), 3)
    fs_total_time = round(statistics.mean(fs_configuration.get_average_total_times()), 3)
    total_time_difference = round(ft_total_time / fs_total_time, 2)

    ft_total_time_after = round(statistics.mean(ft_configuration.get_total_times_after_termination()), 3)
    fs_total_time_after = round(statistics.mean(fs_configuration.get_total_times_after_termination()), 3)
    total_time_after_difference = round(ft_total_time_after / fs_total_time_after, 2)

    if network_size == 250:
      ratio_250 = total_time_difference
    rows.append([network_size,
                 ft_total_time, fs_total_time, total_time_difference,
                 ft_total_time_after, fs_total_time_after, total_time_after_difference])

  write_csv('../report/figures/total-times.csv', headers, rows)
  write_string('../report/figures/total-time-ratio-250.txt', '%.02f' % ratio_250)

def compare_token_bytes(configurations):
  for network_size, (fault_tolerant, fs) in configurations.items():
    bytes_per_token = map(lambda t: t[0] / t[1], zip(fault_tolerant.get_token_bytes(), fault_tolerant.get_tokens()))
    mean = statistics.mean(fault_tolerant.get_token_bytes())

    print("Network size %i Bytes per token: %i" % (network_size, round(mean)))
