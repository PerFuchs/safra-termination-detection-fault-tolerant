import statistics
from collections import defaultdict

from scipy.stats import stats


def compare_safra_versions(configurations):
    filtered = filter(lambda c: c.number_of_nodes <= 1000, configurations)
    fault_free_configurations = list(filter(lambda c: c.fault_percentage == 0, filtered))

    paired_by_network_size = defaultdict(lambda : [None, None])
    for c in fault_free_configurations:
        paired_by_network_size[c.number_of_nodes][int(c.fault_sensitive)] = c
    print(paired_by_network_size)

    compare_tokens(paired_by_network_size)
    compare_tokens_after_termination(paired_by_network_size)

    compare_basic_times(paired_by_network_size)
    compare_token_bytes(paired_by_network_size)
    compare_safra_times(paired_by_network_size)
    compare_safra_times_after_termination(paired_by_network_size)
    compare_total_times(paired_by_network_size)
    compare_total_times_after_termination

def compare_tokens(configurations):
    for network_size, (fault_tolerant, fault_sensitive) in configurations.items():
        show_mean_equality("Tokens", network_size, fault_tolerant.get_tokens(), fault_sensitive.get_tokens())


def compare_tokens_after_termination(configurations):
    for network_size, (fault_tolerant, fault_sensitive) in configurations.items():
        show_mean_equality("Tokens after termination", network_size, fault_tolerant.get_tokens_after_termination(), fault_sensitive.get_tokens_after_termination())


def compare_basic_times(configurations):
    for network_size, (fault_tolerant, fault_sensitive) in configurations.items():
        show_mean_equality("Basic time", network_size, fault_tolerant.get_basic_times(), fault_sensitive.get_basic_times(), True)


def compare_safra_times(configurations):
    for network_size, (fault_tolerant, fault_sensitive) in configurations.items():
        show_mean_equality("Safra time", network_size, fault_tolerant.get_safra_times(), fault_sensitive.get_safra_times(), True)

        total_processing_time_ft = map(sum, zip(fault_tolerant.get_basic_times(), fault_tolerant.get_safra_times()))
        safra_percentage_processing_time_ft = map(lambda t: t[0] / t[1], zip(fault_tolerant.get_safra_times(), total_processing_time_ft))
        safra_percentage_processing_time_mean = statistics.mean(safra_percentage_processing_time_ft)
        print("Safra's ratio of total processing time FT: %i%%" % round(safra_percentage_processing_time_mean * 100))


def compare_safra_times_after_termination(configurations):
    for network_size, (fault_tolerant, fault_sensitive) in configurations.items():
        show_mean_equality("Safra time after termination", network_size, fault_tolerant.get_safra_times_after_termination(), fault_sensitive.get_safra_times_after_termination(), True)

def compare_total_times_after_termination(configurations):
    for network_size, (fault_tolerant, fault_sensitive) in configurations.items():
        show_mean_equality("Total time after termination", network_size, fault_tolerant.get_total_times_after_termination(), fault_sensitive.get_total_times_after_termination(), True)



def compare_total_times(configurations):
    for network_size, (fault_tolerant, fault_sensitive) in configurations.items():
        total_times_ft = fault_tolerant.get_total_times()
        total_times_fs = fault_sensitive.get_total_times()

        show_mean_equality("Total time", network_size, total_times_ft, total_times_fs, True)


def compare_token_bytes(configurations):
    for network_size, (fault_tolerant, fs) in configurations.items():
        bytes_per_token = map(lambda t: t[0] / t[1], zip(fault_tolerant.get_token_bytes(), fault_tolerant.get_tokens()))
        mean = statistics.mean(fault_tolerant.get_token_bytes())

        print("Network size %i Bytes per token: %i" % (network_size, round(mean)))


def show_mean_equality(name, network_size, ft, fs, float=False):
    mean_ft = statistics.mean(ft)
    mean_fs = statistics.mean(fs)

    difference = abs(mean_fs - mean_ft)
    difference_percent = round(difference / min(mean_ft, mean_fs) * 100)

    if float:
        print("%s Network Size: %i Means FT/FS: %f  %f Difference %i%%" % (name, network_size, mean_ft, mean_fs, difference_percent))
    else:
        print("%s Network Size: %i Means FT/FS: %i  %i Difference %i%%" % (name, network_size, round(mean_ft), round(mean_fs), difference_percent))

    print("\n\n")
