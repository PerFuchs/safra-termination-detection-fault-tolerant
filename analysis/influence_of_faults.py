import statistics
from collections import defaultdict, OrderedDict

import plotly
from scipy.stats import stats

import graphing
from graphing import get_scatter_graph_data
from utils import present_linear_relationship, difference_in_percent


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
	present_backup_token_vs_crashes(grouped_by_fault_group_sorted)
	additional_information(grouped_by_fault_group_sorted)


def present_backup_token_vs_crashes(configurations):
	data = {}

	only_faulty_configurations = OrderedDict(filter(lambda i: i[0] != '0', configurations.items()))

	for fault_group, configurations_sorted in only_faulty_configurations.items():
		data[fault_group] = []
		for i, c in enumerate(configurations_sorted):
			data[fault_group].append(graphing.get_box_trace(c.get_backup_tokens(), 'T %s %i' % (fault_group, c.number_of_nodes)))
			data[fault_group].append(graphing.get_box_trace(c.get_number_of_nodes_crashed(),
			                                   'C %s %i' % (fault_group, c.number_of_nodes), 'rgb(255,140,0)'))

	plotly.offline.plot(data['5n'], filename='../graphs/backup_tokens_vs_crashes_5n.html')
	plotly.offline.plot(data['90'], filename='../graphs/backup_tokens_vs_crashes_90.html')


def present_token_and_token_after_termination(configurations):
	data = []

	for fault_group, configurations_sorted in configurations.items():
		for i, c in enumerate(configurations_sorted):
			data.append(graphing.get_box_trace(c.get_tokens(), 'T %s %i' % (fault_group, c.number_of_nodes)))
			data.append(graphing.get_box_trace(c.get_tokens_after_termination(),
			                                   'T %s %i' % (fault_group, c.number_of_nodes), 'rgb(255,140,0)'))

	plotly.offline.plot(data, filename='../graphs/tokens_and_tokens_after_faulty.html')


def additional_information(configurations):
	data_scatter = {}
	for fault_group, sorted_configurations in configurations.items():
		present_linear_relationship(sorted_configurations, 'tokens', True)
		present_linear_relationship(sorted_configurations, 'tokens_after_termination', True)

		for fault_group, configurations_sorted in configurations.items():
			data_scatter[fault_group] = []
			for i, c in enumerate(configurations_sorted):
				data_scatter[fault_group].append(statistics.mean(c.get_tokens()))
				print("T %s %i Increase from fault free: %i" % (fault_group, c.number_of_nodes, difference_in_percent(statistics.mean(c.get_tokens()), statistics.mean(configurations['0'][i].get_tokens()))))
				print("TA %s %i Increase from fault free: %i" % (fault_group, c.number_of_nodes, difference_in_percent(statistics.mean(c.get_tokens_after_termination()), statistics.mean(configurations['0'][i].get_tokens_after_termination()))))
				print("Ratio %s %i %f" % (fault_group, c.number_of_nodes, round(statistics.mean(c.get_tokens()) / statistics.mean(c.get_tokens_after_termination()), 2)))
				print("")

	# plotly.offline.plot([graphing.get_scatter_graph_data(list(range(5)), data, fault_group) for fault_group, data in data_scatter.items()], filename='../graphs/scatter.html')
