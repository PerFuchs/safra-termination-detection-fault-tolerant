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
	present_processing_times(grouped_by_fault_group_sorted)

	present_total_times(grouped_by_fault_group_sorted)
	additional_information(grouped_by_fault_group_sorted)


def present_processing_times(configurations):
	data = []

	for fault_group, configurations_sorted in configurations.items():
		for i, c in enumerate(configurations_sorted):
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


	plotly.offline.plot(data, filename='../graphs/tokens_and_tokens_after_faulty.html')


def additional_information(configurations):
	data_scatter = {}
	for fault_group, sorted_configurations in configurations.items():
		present_linear_relationship(sorted_configurations, 'tokens', True)
		present_linear_relationship(sorted_configurations, 'tokens_after_termination', True)
		present_linear_relationship(sorted_configurations, 'safra_times', True)

	for fault_group, configurations_sorted in configurations.items():
		data_scatter[fault_group] = []
		for i, c in enumerate(configurations_sorted):
			data_scatter[fault_group].append(statistics.mean(c.get_tokens()))
			print("T %s %i Increase from fault free: %f" % (fault_group, c.number_of_nodes, round(statistics.mean(c.get_tokens()) / statistics.mean(configurations['0'][i].get_tokens()), 2)))
			print("Time %s %i Increase from fault free: %f" % (fault_group, c.number_of_nodes, round(statistics.mean(c.get_safra_times()) / statistics.mean(configurations['0'][i].get_safra_times()), 2)))
			print("TA %s %i Increase from fault free: %f11 " % (fault_group, c.number_of_nodes, round(statistics.mean(c.get_tokens_after_termination()) / statistics.mean(configurations['0'][i].get_tokens_after_termination()), 2)))
			print("Time A %s %i Increase from fault free: %f" % (fault_group, c.number_of_nodes, round(statistics.mean(c.get_safra_times_after_termination()) / statistics.mean(configurations['0'][i].get_safra_times_after_termination()), 2)))
			print("Total Time A %s %i Increase from fault free: %f" % (fault_group, c.number_of_nodes, round(statistics.mean(c.get_average_total_times()) / statistics.mean(configurations['0'][i].get_average_total_times()), 2)))
			print("Time A %s %i Increase from fault free: %f" % (fault_group, c.number_of_nodes, round(statistics.mean(c.get_total_times_after_termination()) / statistics.mean(configurations['0'][i].get_total_times_after_termination()), 2)))
			print("Ratio %s %i %f" % (fault_group, c.number_of_nodes, round(statistics.mean(c.get_tokens()) / statistics.mean(c.get_tokens_after_termination()), 2)))
			print("")

	# plotly.offline.plot([graphing.get_scatter_graph_data(list(range(5)), data, fault_group) for fault_group, data in data_scatter.items()], filename='../graphs/scatter.html')
