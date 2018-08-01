import statistics

import plotly
from scipy.stats import stats

from graphing import get_scatter_graph_data


def analyse_influence_of_faults(configurations):
    configurations = filter(lambda c: not c.fault_sensitive, configurations)
    # configurations = filter(lambda c: c.fault_percentage != 0, configurations)
    for c in configurations:
        backup_token_mean = round(statistics.mean(c.get_backup_tokens()))
        faults_mean = round(statistics.mean(c.get_number_of_nodes_crashed()))

        correlation_coefficient, _ = stats.pearsonr(c.get_tokens(), c.get_backup_tokens())
        print("Size %i Number of faults: %i Backup tokens: %i Correlation: %f" % (c.number_of_nodes, faults_mean, backup_token_mean, correlation_coefficient))

        # if  c.fault_percentage == 0.9:
        x_axis = list(range(0, len(c.repetitions)))

        tokens_backup_tokens = zip(c.get_tokens(), c.get_backup_tokens(), c.get_number_of_nodes_crashed())
        tokens_backup_tokens = sorted(tokens_backup_tokens, key=lambda t: t[1])

        data = []
        data.append(get_scatter_graph_data(x_axis, list(map(lambda t: t[0], tokens_backup_tokens)), "Tokens"))
        data.append(get_scatter_graph_data(x_axis, list(map(lambda t: t[1], tokens_backup_tokens)), 'Backup tokens'))
        # data.append(get_scatter_graph_data(x_axis, list(map(lambda t: t[2], tokens_backup_tokens)), 'Crashes'))
        plotly.offline.plot(data, filename='../graphs/%i-%f.html' % (c.number_of_nodes, c.fault_percentage))


    # for c in configurations:
    #     token_after_termination_mean = round(statistics.mean(c.get_tokens_after_termination()))
    #     faults_mean = round(statistics.mean(c.get_number_of_nodes_crashed()))
    #
    #     correlation_coefficient, _ = stats.pearsonr(c.get_tokens_after_termination(), c.get_number_of_nodes_crashed())
    #     print("Size %i Number of faults: %i Tokens after termination tokens: %i Correlation: %f" % (c.number_of_nodes, faults_mean, token_after_termination_mean, correlation_coefficient))
    #
    #     # if  c.fault_percentage == 0.9:
    #     x_axis = list(range(0, len(c.repetitions)))
    #
    #     tokens_backup_tokens = zip(c.get_tokens(), c.get_tokens_after_termination(), c.get_number_of_nodes_crashed())
    #     tokens_backup_tokens = sorted(tokens_backup_tokens, key=lambda t: t[1])
    #
    #     data = []
    #     data.append(get_scatter_graph_data(x_axis, list(map(lambda t: t[0], tokens_backup_tokens)), "Tokens"))
    #     data.append(get_scatter_graph_data(x_axis, list(map(lambda t: t[1], tokens_backup_tokens)), 'Backup tokens'))
    #     data.append(get_scatter_graph_data(x_axis, list(map(lambda t: t[2], tokens_backup_tokens)), 'Crashes'))
    #     plotly.offline.plot(data, filename='../graphs/%i-%f.html' % (c.number_of_nodes, c.fault_percentage))

    # for c in configurations:
    #     token_after_termination_mean = round(statistics.mean(c.get_tokens()))
    #     faults_mean = round(statistics.mean(c.get_number_of_nodes_crashed()))
    #
    #     correlation_coefficient, _ = stats.pearsonr(c.get_tokens_after_termination(), c.get_number_of_nodes_crashed())
    #     print("Size %i Number of faults: %i Tokens after termination tokens: %i Correlation: %f" % (c.number_of_nodes, faults_mean, token_after_termination_mean, correlation_coefficient))
    #
    #     # if  c.fault_percentage == 0.9:
    #     x_axis = list(range(0, len(c.repetitions)))
    #
    #     tokens_backup_tokens = zip(c.get_tokens(), c.get_tokens_after_termination(), c.get_number_of_nodes_crashed())
    #     tokens_backup_tokens = sorted(tokens_backup_tokens, key=lambda t: t[1])
    #
    #     data = []
    #     data.append(get_scatter_graph_data(x_axis, list(map(lambda t: t[0], tokens_backup_tokens)), "Tokens"))
    #     data.append(get_scatter_graph_data(x_axis, list(map(lambda t: t[1], tokens_backup_tokens)), ''))
    #     data.append(get_scatter_graph_data(x_axis, list(map(lambda t: t[2], tokens_backup_tokens)), 'Crashes'))
    #     plotly.offline.plot(data, filename='../graphs/%i-%f.html' % (c.number_of_nodes, c.fault_percentage))


