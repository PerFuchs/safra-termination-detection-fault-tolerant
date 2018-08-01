import itertools
import operator
import statistics
from collections import defaultdict, OrderedDict
from plotly import graph_objs as go
import plotly
from scipy.stats import stats

from graphing import get_scatter_graph_data
from utils import difference_in_percent


def compare_safra_versions(configurations):
	fault_free_configurations = list(filter(lambda c: c.fault_percentage == 0, configurations))

	paired_by_network_size = defaultdict(lambda: [None, None])
	for c in fault_free_configurations:
		paired_by_network_size[c.number_of_nodes][int(c.fault_sensitive)] = c

	paired_by_network_size_sorted = OrderedDict(sorted(paired_by_network_size.items(), key=lambda i: i[0]))

	present_processing_times(paired_by_network_size_sorted)
	present_total_times(paired_by_network_size_sorted)


# TODO use difference symbol should that be in ratio not percent?
def present_processing_times(configurations):
	headers = ['Network size', 'Basic (seconds)', 'FT', 'FS', 'Difference (%)', 'Overhead FT (%)', 'Overhead FS (%)']

	network_sizes = 5
	values = []
	for i in range(len(headers)):
		values.append([-1] * network_sizes)

	for i, (network_size, (ft_configuration, fs_configuration)) in enumerate(configurations.items()):
		basic_time_mean = round(statistics.mean(ft_configuration.get_basic_times()), 3)
		ft_safra_time_mean = round(statistics.mean(ft_configuration.get_safra_times()), 3)
		fs_safra_time_mean = round(statistics.mean(fs_configuration.get_safra_times()), 3)

		safra_time_difference = difference_in_percent(ft_safra_time_mean, fs_safra_time_mean)
		ft_overhead = round(ft_safra_time_mean / basic_time_mean * 100, 2)
		fs_overhead = round(fs_safra_time_mean / basic_time_mean * 100, 2)

		row = [network_size,
		       basic_time_mean, ft_safra_time_mean, fs_safra_time_mean,
		       safra_time_difference,
		       ft_overhead, fs_overhead]
		for j, value in enumerate(row):
			values[j][i] = value

	data = [go.Table(
		header=dict(values=headers),
		cells=dict(values=values)
	)]
	plotly.plotly.plot(data, filename='processing_times.html')


# TODO use difference symbol should that be in ratio not percent?
def present_total_times(configurations):
	headers = ['Network size', 'FT (seconds)', 'FS', 'Difference (%)', 'FT', 'FS', 'Difference (%)']

	network_sizes = 5
	values = []
	for i in range(len(headers)):
		values.append([-1] * network_sizes)

	for i, (network_size, (ft_configuration, fs_configuration)) in enumerate(configurations.items()):
		# Divide by network size because each node records the time spent on him
		ft_total_time = round(statistics.mean(ft_configuration.get_total_times()) / network_size, 3)
		fs_total_time = round(statistics.mean(fs_configuration.get_total_times()) / network_size, 3)
		total_time_difference = round(difference_in_percent(ft_total_time, fs_total_time), 3)

		ft_total_time_after = round(statistics.mean(ft_configuration.get_total_times_after_termination()), 3)
		fs_total_time_after = round(statistics.mean(fs_configuration.get_total_times_after_termination()), 3)
		total_time_after_difference = difference_in_percent(ft_total_time_after, fs_total_time_after)

		row = [network_size,
		       ft_total_time, fs_total_time, total_time_difference,
		       ft_total_time_after, fs_total_time_after, total_time_after_difference]
		for j, value in enumerate(row):
			values[j][i] = value

	data = [go.Table(
		header=dict(values=headers),
		cells=dict(values=values)
	)]
	plotly.plotly.plot(data, filename='total_times.html')
